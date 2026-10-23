# WebDAV test fixtures

Captured `PROPFIND` responses used by the WebDAV unit tests. Each file is a verbatim server
response so that the tests exercise what real servers actually send, not an idealised version.

Live responses are captured with `scripts/capture_webdav_listing.py`, which reads the server
details from a runtime test config:

```sh
python3 scripts/capture_webdav_listing.py \
    --config local/test-configs/webdav.json \
    --output app/src/test/resources/webdav/apache-mod_dav-207.xml
```

The "Expected parse" notes below were produced by running the fixtures through the sardine
implementation the app used before it was replaced, so they record the behaviour the app relies
on rather than a guess. The current client has to match them.

## Captured from a live server

### `apache-mod_dav-207.xml`
Apache `mod_dav`, `Depth: 1` listing of a folder that holds videos, photos, a subfolder and some
junk files.

Expected parse (name / displayName / directory / modified / content length):

| name | displayName | dir | modified | length |
|---|---|---|---|---|
| `IMG` | null | true | 1792624511000 | -1 |
| `Thumbs.db` | null | false | 1741351606000 | 145408 |
| `robinfrcd_hennigsvaer_1080p_H264.m4v` | null | false | 1707148852000 | 76029430 |
| `Roadways of Nevada.jpg` | null | false | 1706889245000 | 4278120 |
| `robinfrcd_nusfjord_1080p_H264.m4v` | null | false | 1719930496000 | 151213479 |
| `⁨Fuji-Hakone-Izu National Park⁩.HEIC` | null | false | 1737358257000 | 1617922 |
| `.DS_Store` | null | false | 1775586669000 | 6148 |
| `6602526-hd_1920_1080_30fps.mp4` | null | false | 1741351598000 | 21542181 |
| `More Media` | null | true | 1792624511000 | -1 |
| `Side street.jpg` | null | false | 1707322879000 | 2492299 |
| `Tokyo tower.jpg` | null | false | 1707505774000 | 2888167 |
| `Train station at Hakone.HEIC` | null | false | 1737358239000 | 2201867 |

Notes: the collection itself is the **first** entry, which is why the provider drops it. There is
no `displayname` property, so it is null. Percent encoded names are decoded, including the
unicode separator characters.

### `apache-mod_dav-subfolder-207.xml`
The same server, a subfolder listing. Holds `.mov` videos, `.jpg` and `.HEIC` photos, two `.gif`
files and a `Thumbs.db`.

### `apache-mod_dav-depth0-207.xml`
The same server with `Depth: 0`, so only the collection itself is returned.

### `apache-mod_dav-401.xml` and `apache-mod_dav-404.xml`
Error responses (HTML bodies from Apache) for an unauthenticated request and a missing path.
Used for the error mapping tests, not for parsing.

## Other servers

### `nextcloud-207.xml`
Nextcloud, which uses the Sabre/dav `d:` prefix and adds `oc:` properties. Sends `displayname`,
`getcontenttype`, `getetag` and `getlastmodified`. Collections report a content length of 0.

### `owncloud-207.xml`
ownCloud, also Sabre based. The collection has an **empty** `displayname` element, which parses
as null.

### `synology-207.xml`
Synology DSM. `D:` prefix, `creationdate` instead of `displayname`, so the display name is null.

### `iis-207.xml`
IIS WebDAV. Includes `displayname`, `getetag` and `supportedlock`.

## Edge cases

### `minimal-207.xml`
Only `href` and `resourcetype`. No dates, no lengths, no display names, so `modified` is null
and the content length is -1.

### `edge-no-lastmodified-207.xml`
One file with a `getlastmodified` and one without. The missing date parses as null while the
content length is still read.

### `edge-multiple-propstat-207.xml`
Two `propstat` blocks per response, one with `200 OK` and one with `404 Not Found`. The
properties from the 200 block are used and the 404 block is ignored.

### `edge-relative-href-207.xml`
`href` values without a leading slash. The name is still the last path segment.

### `edge-encoded-names-207.xml`
Names with a space (`%20`), a literal `+`, a percent sign (`%25`), an emoji (`%F0%9F%8C%8D`) and
a question mark (`%3F`).

Expected parse: `space in name.jpg`, `plus+sign.jpg` (the plus is **not** turned into a space),
`percent%sign.jpg`, `emoji🌍 sunset.jpg`, `question?mark.jpg`.

### `edge-empty-207.xml`
An empty multistatus. Sardine rejected this with `IOException("Not a valid DAV response")`, so
an empty folder used to be reported as a connection error rather than as an empty listing. The
current client parses it as an empty listing, which is the sensible behaviour.
