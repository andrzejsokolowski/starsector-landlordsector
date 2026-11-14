"""Makes the exterior pictures through the Gemini API, the way Andrzej makes them by hand.

For each home that has no picture yet:
  1. a new chat gets the variant 1 prompt from art-prompts.html (jungle and water homes also get
     the Terran picture of the same size, as the prompts page says)  -> <size>_<planet>.jpg
  2. the same chat gets only the VARIANT 2 text                       -> <size>_<planet>_alt.jpg,
     flipped left to right, because Gemini puts the alt home on the left.

Commands (run from anywhere):
  python make_art.py status              what exists, what is missing, what the rest would cost
  python make_art.py make [--only X]     make the missing pictures (asks first unless --yes)
  python make_art.py redo                remake every rejected picture from its review comment
  python make_art.py import [FOLDER]     copy Andrzej's hand-made pictures (default: Downloads)
  python make_art.py models              list the image models this API key can use

The API key comes from the GEMINI_API_KEY environment variable (set it once with
`setx GEMINI_API_KEY <key>`; the script also reads it from the Windows user settings, so a
terminal opened before setx works too). The key is never printed or saved anywhere else.
"""
import argparse
import io
import os
import shutil
import sys
import threading
import time
from concurrent.futures import ThreadPoolExecutor

sys.path.insert(0, os.path.dirname(__file__))
import artlib  # noqa: E402
from artlib import ART, REJECTED, find, homes, update, load_review, seed  # noqa: E402

DEFAULT_MODEL = 'gemini-3-pro-image-preview'   # Nano Banana Pro, what the Gemini app uses on Pro
PRICE = 0.134                                    # dollars per 2K picture on that model (Nov 2026)

_print_lock = threading.Lock()
_made = [0]


def log(msg):
    line = f'[{time.strftime("%H:%M:%S")}] {msg}'
    with _print_lock:
        print(line, flush=True)
        os.makedirs(ART, exist_ok=True)
        with open(os.path.join(ART, 'maker-log.txt'), 'a', encoding='utf-8') as f:
            f.write(line + '\n')


def api_key():
    k = os.environ.get('GEMINI_API_KEY') or os.environ.get('GOOGLE_API_KEY')
    if k:
        return k
    try:
        import winreg
        with winreg.OpenKey(winreg.HKEY_CURRENT_USER, 'Environment') as reg:
            return winreg.QueryValueEx(reg, 'GEMINI_API_KEY')[0]
    except Exception:
        return None


class Maker:
    def __init__(self, model, size):
        from google import genai
        from google.genai import types
        key = api_key()
        if not key:
            sys.exit('No API key. Create one at https://aistudio.google.com/apikey, then run:\n'
                     '  setx GEMINI_API_KEY <your key>\nand run this again.')
        self.types = types
        self.client = genai.Client(api_key=key)
        self.model = model
        self.config = types.GenerateContentConfig(
            response_modalities=['TEXT', 'IMAGE'],
            image_config=types.ImageConfig(aspect_ratio='16:9', image_size=size))

    def chat(self):
        return self.client.chats.create(model=self.model, config=self.config)

    def picture_part(self, path):
        with open(path, 'rb') as f:
            data = f.read()
        mime = 'image/png' if path.lower().endswith('.png') else 'image/jpeg'
        return self.types.Part.from_bytes(data=data, mime_type=mime)

    def send(self, chat, parts, what):
        """Send one message; return the picture bytes. Retries busy errors and text-only answers."""
        message = parts
        for attempt in range(1, 5):
            try:
                resp = chat.send_message(message)
            except Exception as e:  # rate limits, server errors, network
                text = str(e)
                if 'API key' in text or 'PERMISSION_DENIED' in text or 'NOT_FOUND' in text or 'billing' in text.lower():
                    raise
                wait = 20 * attempt
                log(f'  {what}: error ({text[:160]}), trying again in {wait} s')
                time.sleep(wait)
                continue
            image, words = None, []
            for c in (resp.candidates or []):
                for p in (c.content.parts if c.content and c.content.parts else []):
                    if getattr(p, 'thought', False):
                        continue
                    if p.inline_data and p.inline_data.data:
                        image = p.inline_data.data
                    elif p.text:
                        words.append(p.text.strip())
            if words:
                log(f'  {what}: Gemini said: {" ".join(words)[:300]}')
            if image:
                _made[0] += 1
                return image
            log(f'  {what}: no picture in the answer (try {attempt})')
            message = ['Make the picture now, as one single image, following everything above.']
        raise RuntimeError(f'{what}: no picture after 4 tries')


def save(data, stem, flip):
    from PIL import Image, ImageOps
    im = Image.open(io.BytesIO(data)).convert('RGB')
    if flip:
        im = ImageOps.mirror(im)
    for e in artlib.EXTS:  # one file per stem
        old = os.path.join(ART, stem + e)
        if os.path.exists(old):
            os.remove(old)
    path = os.path.join(ART, stem + '.jpg')
    im.save(path, quality=95)
    thumb = os.path.join(artlib.THUMBS, stem + '.jpg')
    if os.path.exists(thumb):
        os.remove(thumb)
    return path


def archive(stem):
    """Keep a rejected picture in art-source/rejected/ before it is replaced."""
    f = find(stem)
    if not f:
        return
    os.makedirs(REJECTED, exist_ok=True)
    n = 1
    while True:
        dst = os.path.join(REJECTED, f'{stem}_{n}{os.path.splitext(f)[1]}')
        if not os.path.exists(dst):
            break
        n += 1
    shutil.copy2(f, dst)


def selected(args):
    hs = homes()
    if args.only:
        want = [w.strip() for w in args.only.split(',') if w.strip()]
        hs = [h for h in hs if any(w in (h.size['id'], h.planet['id'], h.stem, h.key) or h.stem.startswith(w + '_')
                                   for w in want)]
    return hs


def todo(hs):
    return [(h, not find(h.stem), not find(h.alt_stem)) for h in hs if not find(h.stem) or not find(h.alt_stem)]


# ---------------------------------------------------------------- make

def make_home(m, h, need_v1, need_alt):
    try:
        chat = m.chat()
        if need_v1:
            parts = []
            if h.needs_terran:
                parts.append(m.picture_part(find(h.terran_stem)))
            parts.append(h.prompt)
            log(f'{h.label()}: making variant 1')
            save(m.send(chat, parts, h.stem), h.stem, flip=False)
            update(h.stem, status='new', comment='', fresh=False, flipped=False, event='made')
            log(f'{h.label()}: saved {h.stem}.jpg')
            if need_alt:
                log(f'{h.label()}: making the alt in the same chat')
                save(m.send(chat, [h.alt_text], h.alt_stem), h.alt_stem, flip=True)
                update(h.alt_stem, status='new', comment='', fresh=False, flipped=True, event='made')
                log(f'{h.label()}: saved {h.alt_stem}.jpg (flipped)')
        elif need_alt:
            log(f'{h.label()}: making the alt from the existing variant 1')
            text = 'This is variant 1 of this home, made from the prompt below.\n\n' + h.prompt + '\n\n' + h.alt_text
            save(m.send(chat, [m.picture_part(find(h.stem)), text], h.alt_stem), h.alt_stem, flip=True)
            update(h.alt_stem, status='new', comment='', fresh=False, flipped=True, event='made')
            log(f'{h.label()}: saved {h.alt_stem}.jpg (flipped)')
    except Exception as e:
        log(f'{h.label()}: FAILED: {str(e)[:300]}')


def cmd_make(args):
    seed()
    work = todo(selected(args))
    if args.limit:
        work = work[:args.limit]
    pics = sum(int(a) + int(b) for _, a, b in work)
    if not work:
        print('Nothing to make: every selected home has both pictures.')
        return
    print(f'{len(work)} homes, {pics} pictures, about ${pics * PRICE:.2f} (more if some need a second try).')
    for h, a, b in work:
        print(f'  {h.label():32} ' + ' + '.join(x for x, need in (('variant 1', a), ('alt', b)) if need))
    if not args.yes and input('Type yes to start: ').strip().lower() != 'yes':
        return
    m = Maker(args.model, args.size)
    # Jungle and water homes need the Terran picture of their size first.
    first = [w for w in work if not (w[1] and w[0].needs_terran and not find(w[0].terran_stem))]
    later = [w for w in work if w not in first]
    for batch in (first, later):
        with ThreadPoolExecutor(max_workers=args.jobs) as pool:
            for h, a, b in batch:
                if a and h.needs_terran and not find(h.terran_stem):
                    log(f'{h.label()}: skipped, {h.terran_stem} is missing')
                    continue
                pool.submit(make_home, m, h, a, b)
    log(f'Done: {_made[0]} pictures made, about ${_made[0] * PRICE:.2f}.')


# ---------------------------------------------------------------- redo

def redo_one(m, h, alt, entry):
    stem = h.alt_stem if alt else h.stem
    comment = entry.get('comment', '').strip()
    fresh = entry.get('fresh') or not find(stem)
    what = h.label(alt)
    try:
        chat = m.chat()
        if not fresh:
            context = h.prompt + ('\n\n' + h.alt_text if alt else '')
            text = ('This picture was made from the prompt below, but it needs changes.\n\n'
                    f'CHANGES (the most important part):\n{comment}\n\n'
                    'Keep everything else in the picture the same, and keep the home on the same side of the picture.\n\n'
                    f'THE ORIGINAL PROMPT:\n{context}')
            archive(stem)
            log(f'{what}: fixing it: {comment[:120]}')
            data = m.send(chat, [m.picture_part(find(stem)), text], stem)
            flip = False
        elif not alt:
            parts = [m.picture_part(find(h.terran_stem))] if h.needs_terran and find(h.terran_stem) else []
            parts.append(h.prompt + f'\n\nALSO (the most important part): {comment}')
            archive(stem)
            log(f'{what}: starting over: {comment[:120]}')
            data = m.send(chat, parts, stem)
            flip = False
        else:
            if not find(h.stem):
                log(f'{what}: skipped, variant 1 is missing')
                return
            text = ('This is variant 1 of this home, made from the prompt below.\n\n' + h.prompt + '\n\n' + h.alt_text
                    + f'\n- Also (the most important part): {comment}')
            archive(stem)
            log(f'{what}: starting over: {comment[:120]}')
            data = m.send(chat, [m.picture_part(find(h.stem)), text], stem)
            flip = True
        save(data, stem, flip=flip)
        update(stem, status='new', comment='', fresh=False, flipped=alt, event='remade' + (' from scratch' if fresh else ''))
        log(f'{what}: saved {stem}.jpg, ready for review')
    except Exception as e:
        log(f'{what}: FAILED: {str(e)[:300]}')


def cmd_redo(args):
    r = load_review()
    work = []
    for h in selected(args):
        for alt in (False, True):
            e = r.get(h.alt_stem if alt else h.stem)
            if e and e.get('status') == 'rejected':
                work.append((h, alt, e))
    if not work:
        print('No rejected pictures.')
        return
    print(f'{len(work)} picture(s) to remake, about ${len(work) * PRICE:.2f}:')
    for h, alt, e in work:
        print(f'  {h.label(alt):38} {"start over" if e.get("fresh") else "fix"}: {e.get("comment", "")[:80]}')
    if not args.yes and input('Type yes to start: ').strip().lower() != 'yes':
        return
    m = Maker(args.model, args.size)
    with ThreadPoolExecutor(max_workers=args.jobs) as pool:
        for h, alt, e in work:
            pool.submit(redo_one, m, h, alt, e)
    log(f'Done: {_made[0]} pictures made, about ${_made[0] * PRICE:.2f}.')


# ---------------------------------------------------------------- the rest

def cmd_status(args):
    r = seed()
    hs = selected(args)
    counts = {}
    for h in hs:
        for stem in (h.stem, h.alt_stem):
            st = r.get(stem, {}).get('status', 'missing') if find(stem) else 'missing'
            counts[st] = counts.get(st, 0) + 1
    print('Pictures:', ', '.join(f'{k} {v}' for k, v in sorted(counts.items())))
    for size in dict.fromkeys(h.size['name'] for h in hs):
        row = []
        for h in (x for x in hs if x.size['name'] == size):
            mark = ''.join('x' if find(s) else '.' for s in (h.stem, h.alt_stem))
            row.append(f"{h.planet['id'][:5]}:{mark}")
        print(f'  {size:12} ' + ' '.join(row))
    missing = counts.get('missing', 0)
    print(f'Missing pictures would cost about ${missing * PRICE:.2f}.')


def cmd_import(args):
    src = args.folder or os.path.join(os.path.expanduser('~'), 'Downloads')
    os.makedirs(ART, exist_ok=True)
    n = 0
    for h in homes():
        for stem in (h.stem, h.alt_stem):
            if find(stem):
                continue
            for e in artlib.EXTS:
                f = os.path.join(src, stem + e)
                if os.path.exists(f):
                    shutil.copy2(f, os.path.join(ART, stem + e))
                    n += 1
                    break
    seed()
    print(f'Copied {n} pictures from {src} (the originals stay where they are).')


def cmd_models(args):
    m = Maker(args.model, args.size)
    for x in m.client.models.list():
        if 'image' in x.name.lower():
            print(x.name, '-', x.display_name)


def main():
    ap = argparse.ArgumentParser(description='Landlordsector picture maker')
    ap.add_argument('command', choices=['status', 'make', 'redo', 'import', 'models'])
    ap.add_argument('folder', nargs='?', help='for import: where the pictures are (default Downloads)')
    ap.add_argument('--only', help='sizes, planets or file names, comma separated (shouse, house, frozen, villa_toxic)')
    ap.add_argument('--limit', type=int, help='make at most this many homes')
    ap.add_argument('--jobs', type=int, default=3, help='pictures made at the same time (default 3)')
    ap.add_argument('--model', default=DEFAULT_MODEL)
    ap.add_argument('--size', default='2K', choices=['1K', '2K', '4K'])
    ap.add_argument('--yes', action='store_true', help='do not ask before spending')
    args = ap.parse_args()
    {'status': cmd_status, 'make': cmd_make, 'redo': cmd_redo, 'import': cmd_import, 'models': cmd_models}[args.command](args)


if __name__ == '__main__':
    main()
