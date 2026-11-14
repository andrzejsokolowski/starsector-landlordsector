"""Review gallery for the exterior pictures: approve, reject with a comment, or flip.

  python gallery.py            then open http://127.0.0.1:8765

Reviews are saved to art-source/review.json; `python make_art.py redo` remakes the rejected ones.
"""
import json
import os
import sys
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from urllib.parse import urlparse

sys.path.insert(0, os.path.dirname(__file__))
import artlib  # noqa: E402
from artlib import ART, THUMBS, find, homes, load_review, seed, update  # noqa: E402

HERE = os.path.dirname(os.path.abspath(__file__))
PORT = int(os.environ.get('ART_GALLERY_PORT', '8765'))


def stems():
    out = {}
    for h in homes():
        out[h.stem] = (h, False)
        out[h.alt_stem] = (h, True)
    return out


def state():
    r = load_review()
    items = []
    for stem, (h, alt) in stems().items():
        f = find(stem)
        e = r.get(stem, {})
        items.append({
            'stem': stem, 'label': h.label(alt), 'size': h.size['id'], 'sizeName': h.size['name'],
            'planet': h.planet['id'], 'planetName': h.planet['name'], 'alt': alt,
            'exists': bool(f), 'v': int(os.path.getmtime(f)) if f else 0,
            'status': (e.get('status', 'new') if f else 'missing'),
            'comment': e.get('comment', ''), 'fresh': e.get('fresh', False),
            'history': e.get('history', [])[-6:],
        })
    tail = ''
    logf = os.path.join(ART, 'maker-log.txt')
    if os.path.exists(logf):
        with open(logf, encoding='utf-8') as fh:
            lines = fh.read().splitlines()
            tail = lines[-1] if lines else ''
    return {'items': items, 'log': tail}


def thumb(stem, src):
    from PIL import Image
    os.makedirs(THUMBS, exist_ok=True)
    t = os.path.join(THUMBS, stem + '.jpg')
    if not os.path.exists(t) or os.path.getmtime(t) < os.path.getmtime(src):
        im = Image.open(src).convert('RGB')
        im.thumbnail((900, 900))
        im.save(t, quality=85)
    return t


def flip(stem):
    from PIL import Image, ImageOps
    f = find(stem)
    im = ImageOps.mirror(Image.open(f).convert('RGB'))
    if f.lower().endswith('.png'):
        im.save(f)
    else:
        im.save(f, quality=95)
    e = load_review().get(stem, {})
    update(stem, flipped=not e.get('flipped', False), event='flipped')


class Handler(BaseHTTPRequestHandler):
    def log_message(self, *a):
        pass

    def send(self, code, body, ctype='application/json', cache=False):
        if isinstance(body, str):
            body = body.encode('utf-8')
        self.send_response(code)
        self.send_header('Content-Type', ctype)
        self.send_header('Content-Length', str(len(body)))
        self.send_header('Cache-Control', 'max-age=31536000' if cache else 'no-store')
        self.end_headers()
        self.wfile.write(body)

    def do_GET(self):
        path = urlparse(self.path).path
        if path in ('/', '/index.html'):
            with open(os.path.join(HERE, 'gallery.html'), encoding='utf-8') as fh:
                return self.send(200, fh.read(), 'text/html; charset=utf-8')
        if path == '/api/state':
            return self.send(200, json.dumps(state()))
        for prefix in ('/full/', '/thumb/'):
            if path.startswith(prefix):
                stem = path[len(prefix):]
                if stem not in stems() or not find(stem):
                    return self.send(404, '{}')
                f = find(stem) if prefix == '/full/' else thumb(stem, find(stem))
                ctype = 'image/png' if f.lower().endswith('.png') else 'image/jpeg'
                with open(f, 'rb') as fh:
                    return self.send(200, fh.read(), ctype, cache=True)
        self.send(404, '{}')

    def do_POST(self):
        path = urlparse(self.path).path
        n = int(self.headers.get('Content-Length', '0'))
        try:
            body = json.loads(self.rfile.read(n) or b'{}')
        except ValueError:
            return self.send(400, '{"error": "bad json"}')
        stem = body.get('stem')
        if stem not in stems() or not find(stem):
            return self.send(404, '{"error": "no such picture"}')
        if path == '/api/review':
            status = body.get('status')
            if status not in ('new', 'approved', 'rejected'):
                return self.send(400, '{"error": "bad status"}')
            comment = (body.get('comment') or '').strip()
            update(stem, status=status, comment=comment if status == 'rejected' else '',
                   fresh=bool(body.get('fresh')) if status == 'rejected' else False, event=status)
            return self.send(200, '{"ok": true}')
        if path == '/api/flip':
            flip(stem)
            return self.send(200, '{"ok": true}')
        self.send(404, '{}')


def main():
    seed()
    srv = ThreadingHTTPServer(('127.0.0.1', PORT), Handler)
    print(f'Gallery on http://127.0.0.1:{PORT}  (Ctrl+C to stop)', flush=True)
    try:
        srv.serve_forever()
    except KeyboardInterrupt:
        pass


if __name__ == '__main__':
    main()
