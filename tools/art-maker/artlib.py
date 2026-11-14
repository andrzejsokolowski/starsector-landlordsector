"""Shared parts of the picture maker and the review gallery.

Pictures live in art-source/ (gitignored) under Andrzej's own names:
  <size>_<planet>.jpg       variant 1
  <size>_<planet>_alt.jpg   variant 2, flipped so the home stands on the right
with size shack, studio, shouse, house, villa, estate and planet as in the prompts page,
except gas_giant, which is written gasgiant.

review.json in art-source/ holds the review state of every picture, keyed by file stem:
  {"status": "new" | "approved" | "rejected", "comment": "...", "fresh": false,
   "flipped": true, "history": [{"at": "...", "event": "...", "comment": "..."}]}
"""
import json
import os
import re
import threading
import time

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), '..', '..'))
ART = os.path.join(ROOT, 'art-source')
PROMPTS_HTML = os.path.join(ROOT, 'art-prompts.html')
REVIEW = os.path.join(ART, 'review.json')
REJECTED = os.path.join(ART, 'rejected')
THUMBS = os.path.join(ART, '.thumbs')

SIZE_FILE = {'small_house': 'shouse'}
PLANET_FILE = {'gas_giant': 'gasgiant'}
EXTS = ('.jpg', '.jpeg', '.png', '.webp')

VARIANT2 = (
    'VARIANT 2 (a second picture of this home, so two homes of the same size on the same world do not look the same):\n'
    '- Keep everything above exactly as it is: the art style, the camera and framing, the world, the light, the time of day, '
    'and every MUST and MUST NOT rule.\n'
    '- Change only the building and its yard: a different floor plan and outline, a different roof shape, different window and '
    'door positions, a different pattern of panels and patches, and a different accent color.\n'
    '- Change the props: a different object at the left edge of the picture and different clutter on the ground.\n'
    '- It must still clearly be a {size} on this world, the same size of home as variant 1.')


def load_data():
    s = open(PROMPTS_HTML, encoding='utf-8').read()
    m = re.search(r'const DATA = (\{.*?\});\n', s, re.S)
    return json.loads(m.group(1))


class Home:
    """One home: a size on a planet, with its two pictures."""

    def __init__(self, size, planet, prompt):
        self.size = size            # dict id/name from the prompts page
        self.planet = planet        # dict id/name/shared
        self.key = size['id'] + '_' + planet['id']
        self.prompt = prompt
        self.stem = SIZE_FILE.get(size['id'], size['id']) + '_' + PLANET_FILE.get(planet['id'], planet['id'])
        self.alt_stem = self.stem + '_alt'

    @property
    def alt_text(self):
        return VARIANT2.format(size=self.size['name'].lower())

    @property
    def needs_terran(self):
        """Jungle and water homes keep the Terran building; the page says to attach that picture."""
        return self.planet.get('shared') and self.planet['id'] != 'terran' and self.size['id'] != 'shack'

    @property
    def terran_stem(self):
        return SIZE_FILE.get(self.size['id'], self.size['id']) + '_terran'

    def label(self, alt=False):
        return f"{self.size['name']}, {self.planet['name']}" + (' (alt)' if alt else '')


def homes():
    d = load_data()
    out = []
    for s in d['sizes']:
        for p in d['planets']:
            out.append(Home(s, p, d['prompts'][s['id'] + '_' + p['id']]))
    return out


def find(stem):
    """The picture file for a stem, or None."""
    for e in EXTS:
        f = os.path.join(ART, stem + e)
        if os.path.exists(f):
            return f
    return None


_lock = threading.RLock()


def now():
    return time.strftime('%Y-%m-%d %H:%M:%S')


def load_review():
    with _lock:
        if not os.path.exists(REVIEW):
            return {}
        with open(REVIEW, encoding='utf-8') as f:
            return json.load(f)


def save_review(r):
    with _lock:
        tmp = REVIEW + '.tmp'
        with open(tmp, 'w', encoding='utf-8') as f:
            json.dump(r, f, indent=1, ensure_ascii=False)
        os.replace(tmp, REVIEW)


def update(stem, **fields):
    """Change one picture's review entry and log the change."""
    with _lock:
        r = load_review()
        e = r.setdefault(stem, {'status': 'new', 'comment': '', 'fresh': False, 'history': []})
        event = fields.pop('event', None)
        e.update(fields)
        if event:
            e['history'].append({'at': now(), 'event': event, 'comment': fields.get('comment', e.get('comment', ''))})
        save_review(r)
        return e


def seed():
    """Pictures that exist but have no entry were made by hand before the maker: approved."""
    with _lock:
        r = load_review()
        changed = False
        for h in homes():
            for stem, alt in ((h.stem, False), (h.alt_stem, True)):
                if stem not in r and find(stem):
                    r[stem] = {'status': 'approved', 'comment': '', 'fresh': False, 'flipped': alt,
                               'history': [{'at': now(), 'event': 'made by hand', 'comment': ''}]}
                    changed = True
        if changed:
            save_review(r)
        return r
