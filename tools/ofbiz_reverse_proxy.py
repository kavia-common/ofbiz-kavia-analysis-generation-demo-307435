from __future__ import annotations

import argparse
import http.client
import ssl
import sys
import urllib.parse
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from typing import Dict, Iterable, Optional, Tuple


def _parse_upstream(upstream: str) -> Tuple[str, str, int]:
    parsed = urllib.parse.urlparse(upstream)
    if parsed.scheme not in ("http", "https"):
        raise ValueError(f"Unsupported upstream scheme: {parsed.scheme}")
    if not parsed.hostname:
        raise ValueError("Upstream must include a hostname")
    port = parsed.port
    if port is None:
        port = 443 if parsed.scheme == "https" else 80
    return parsed.scheme, parsed.hostname, port


class ReverseProxyHandler(BaseHTTPRequestHandler):
    """
    Minimal reverse proxy handler.

    This exists to satisfy a platform constraint: the preview expects OFBiz to be available on port 3001,
    but OFBiz itself binds to internal Tomcat ports (e.g., 8080/8443). We expose a stable external port
    by proxying to OFBiz's internal HTTPS connector.
    """

    upstream_scheme: str = "https"
    upstream_host: str = "127.0.0.1"
    upstream_port: int = 8443
    upstream_ssl_context: Optional[ssl.SSLContext] = None

    server_version = "kavia-ofbiz-proxy/1.0"

    def log_message(self, fmt: str, *args) -> None:
        # Keep proxy logs concise in preview.
        sys.stderr.write("%s - - [%s] %s\n" % (self.address_string(), self.log_date_time_string(), fmt % args))

    def _healthz(self) -> None:
        # Return 200 immediately so the container can be marked ready.
        # (OFBiz startup can be heavy; proxy availability is the readiness signal.)
        self.send_response(200)
        self.send_header("Content-Type", "application/json")
        self.end_headers()
        self.wfile.write(b'{"status":"ok","service":"ofbiz-proxy"}')

    def _read_body(self) -> bytes:
        length = self.headers.get("Content-Length")
        if not length:
            return b""
        try:
            n = int(length)
        except ValueError:
            return b""
        return self.rfile.read(n) if n > 0 else b""

    def _filtered_request_headers(self) -> Dict[str, str]:
        hop_by_hop = {
            "connection",
            "keep-alive",
            "proxy-authenticate",
            "proxy-authorization",
            "te",
            "trailer",
            "transfer-encoding",
            "upgrade",
        }
        headers: Dict[str, str] = {}
        for k, v in self.headers.items():
            if k.lower() in hop_by_hop:
                continue
            # Host will be set by http.client based on upstream host, but keeping it explicit is fine.
            if k.lower() == "host":
                continue
            headers[k] = v
        headers["Host"] = f"{self.upstream_host}:{self.upstream_port}"
        return headers

    def _rewrite_location(self, value: str) -> str:
        """
        Rewrite redirects pointing at OFBiz internal ports back to the proxy host/port.

        This is best-effort and primarily avoids sending users to :8443 directly.
        """
        try:
            parsed = urllib.parse.urlparse(value)
        except Exception:
            return value

        if parsed.scheme not in ("http", "https") or not parsed.netloc:
            return value

        # If the upstream redirects to its own host/ports, rewrite to the externally visible host.
        if parsed.hostname in (self.upstream_host, "localhost", "127.0.0.1"):
            external_host = self.headers.get("Host", "")
            if external_host:
                return urllib.parse.urlunparse(parsed._replace(netloc=external_host))
        return value

    def _proxy(self) -> None:
        if self.path in ("/healthz", "/health"):
            self._healthz()
            return

        body = self._read_body()
        headers = self._filtered_request_headers()

        # Prepare upstream connection
        if self.upstream_scheme == "https":
            conn = http.client.HTTPSConnection(
                self.upstream_host,
                self.upstream_port,
                timeout=30,
                context=self.upstream_ssl_context,
            )
        else:
            conn = http.client.HTTPConnection(self.upstream_host, self.upstream_port, timeout=30)

        try:
            conn.request(self.command, self.path, body=body, headers=headers)
            resp = conn.getresponse()
            resp_body = resp.read()
        except Exception as exc:
            self.send_response(502)
            self.send_header("Content-Type", "text/plain; charset=utf-8")
            self.end_headers()
            self.wfile.write(f"Bad gateway: {exc}".encode("utf-8"))
            return
        finally:
            try:
                conn.close()
            except Exception:
                pass

        self.send_response(resp.status, resp.reason)

        hop_by_hop_resp = {"connection", "keep-alive", "proxy-authenticate", "proxy-authorization", "te", "trailer", "upgrade"}
        for k, v in resp.getheaders():
            lk = k.lower()
            if lk in hop_by_hop_resp:
                continue
            if lk == "location":
                v = self._rewrite_location(v)
            # Avoid double content-length if BaseHTTPRequestHandler decides to add one.
            if lk == "content-length":
                continue
            self.send_header(k, v)

        self.send_header("Content-Length", str(len(resp_body)))
        self.end_headers()
        if self.command != "HEAD":
            self.wfile.write(resp_body)

    def do_GET(self) -> None:  # noqa: N802
        self._proxy()

    def do_POST(self) -> None:  # noqa: N802
        self._proxy()

    def do_PUT(self) -> None:  # noqa: N802
        self._proxy()

    def do_DELETE(self) -> None:  # noqa: N802
        self._proxy()

    def do_PATCH(self) -> None:  # noqa: N802
        self._proxy()

    def do_OPTIONS(self) -> None:  # noqa: N802
        self._proxy()

    def do_HEAD(self) -> None:  # noqa: N802
        self._proxy()


def main() -> int:
    parser = argparse.ArgumentParser(description="Expose OFBiz on the preview port via a simple reverse proxy.")
    parser.add_argument("--listen-host", default="0.0.0.0")
    parser.add_argument("--listen-port", type=int, default=3001)
    parser.add_argument("--upstream", default="https://127.0.0.1:8443")
    args = parser.parse_args()

    scheme, host, port = _parse_upstream(args.upstream)
    ReverseProxyHandler.upstream_scheme = scheme
    ReverseProxyHandler.upstream_host = host
    ReverseProxyHandler.upstream_port = port

    if scheme == "https":
        # OFBiz uses a self-signed demo certificate by default.
        ctx = ssl.create_default_context()
        ctx.check_hostname = False
        ctx.verify_mode = ssl.CERT_NONE
        ReverseProxyHandler.upstream_ssl_context = ctx

    httpd = ThreadingHTTPServer((args.listen_host, args.listen_port), ReverseProxyHandler)
    sys.stderr.write(f"[kavia] Proxy listening on {args.listen_host}:{args.listen_port} -> {scheme}://{host}:{port}\n")
    httpd.serve_forever()
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
