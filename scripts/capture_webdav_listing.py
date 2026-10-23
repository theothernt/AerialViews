#!/usr/bin/env python3
"""
Capture a WebDAV PROPFIND response for use as a test fixture.

The captured response is written verbatim so that it can be replayed from unit tests. This keeps
a corpus of real server responses, which is what tells us whether a WebDAV client parses what
real servers actually send.

Examples
--------
Capture from a config file in local/test-configs:

    python3 scripts/capture_webdav_listing.py \
        --config local/test-configs/webdav.json \
        --output app/src/test/resources/webdav/apache-mod_dav-207.xml

Capture a specific path on the same server (the path is relative to the server root):

    python3 scripts/capture_webdav_listing.py \
        --config local/test-configs/webdav.json \
        --path /Video/Aerial/Test/IMG/More%20Media \
        --output app/src/test/resources/webdav/apache-mod_dav-subfolder-207.xml

Capture an error response (no credentials are sent):

    python3 scripts/capture_webdav_listing.py \
        --config local/test-configs/webdav.json \
        --no-auth \
        --output app/src/test/resources/webdav/apache-mod_dav-401.xml
"""

import argparse
import base64
import json
import os
import sys
import urllib.error
import urllib.request
from urllib.parse import urlsplit

PROPFIND_BODY = (
    '<?xml version="1.0" encoding="utf-8"?>\n'
    '<D:propfind xmlns:D="DAV:"><D:prop>'
    '<D:resourcetype/><D:getlastmodified/><D:getcontentlength/><D:displayname/>'
    '</D:prop></D:propfind>'
)


def build_url(base_url, path):
    if not path:
        return base_url
    if path.startswith("http://") or path.startswith("https://"):
        return path
    return base_url.rstrip("/") + "/" + path.lstrip("/")


def read_config(config_path):
    """Read the webdav block from a runtime test config."""
    with open(config_path, encoding="utf-8") as handle:
        config = json.load(handle)
    webdav = config.get("providers", {}).get("webdav")
    if not webdav:
        sys.exit(f"No webdav provider block found in {config_path}")

    scheme = str(webdav.get("scheme", "HTTP")).lower()
    host = webdav["hostName"]
    root = f"{scheme}://{host}"
    path = webdav.get("pathName", "")
    return root, path, webdav.get("userName", ""), webdav.get("password", "")


def propfind(url, user, password, depth, timeout):
    request = urllib.request.Request(
        url,
        data=PROPFIND_BODY.encode("utf-8"),
        method="PROPFIND",
    )
    request.add_header("Depth", str(depth))
    request.add_header("Content-Type", "text/xml; charset=utf-8")
    if user:
        token = base64.b64encode(f"{user}:{password}".encode("utf-8")).decode("ascii")
        request.add_header("Authorization", f"Basic {token}")

    try:
        with urllib.request.urlopen(request, timeout=timeout) as response:
            return response.status, response.headers.get("Content-Type", ""), response.read()
    except urllib.error.HTTPError as error:
        # Error responses are worth capturing too, the client maps them to user facing messages.
        return error.code, error.headers.get("Content-Type", ""), error.read()
    except urllib.error.URLError as error:
        sys.exit(f"Could not reach {url}: {error.reason}")


def main():
    parser = argparse.ArgumentParser(description="Capture a WebDAV PROPFIND response as a test fixture")
    parser.add_argument("--config", help="Runtime test config to read the server details from")
    parser.add_argument("--url", help="Base URL of the WebDAV server")
    parser.add_argument("--user", help="User name")
    parser.add_argument("--password", help="Password")
    parser.add_argument("--path", help="Path to request, relative to the base URL")
    parser.add_argument("--depth", type=int, default=1, help="Depth header, defaults to 1")
    parser.add_argument("--timeout", type=int, default=30, help="Timeout in seconds")
    parser.add_argument("--no-auth", action="store_true", help="Do not send credentials")
    parser.add_argument("--output", required=True, help="File to write the response to")
    args = parser.parse_args()

    if args.config:
        root, config_path, user, password = read_config(args.config)
        if args.url:
            root = args.url
        if args.user is not None:
            user = args.user
        if args.password is not None:
            password = args.password
    else:
        if not args.url:
            sys.exit("Either --config or --url is required")
        root, config_path, user, password = args.url, "", args.user or "", args.password or ""

    if args.no_auth:
        user, password = "", ""

    # The path is always relative to the server root. Without --path the path from the config
    # is used, so a config points at the folder to test.
    target = build_url(root, args.path or config_path)
    status, content_type, body = propfind(target, user, password, args.depth, args.timeout)

    output_dir = os.path.dirname(args.output)
    if output_dir:
        os.makedirs(output_dir, exist_ok=True)
    with open(args.output, "wb") as handle:
        handle.write(body)

    print(f"{target}")
    print(f"  status      : {status}")
    print(f"  content type: {content_type}")
    print(f"  bytes       : {len(body)}")
    print(f"  written to  : {args.output}")
    if status not in (207, 200):
        print("  note        : this is an error response, check it is the one you want to keep")


if __name__ == "__main__":
    main()
