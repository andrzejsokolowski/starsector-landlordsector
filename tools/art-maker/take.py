"""Moves the newest picture downloaded from the Gemini app into art-source under its home's name.

  python take.py shouse_frozen            variant 1
  python take.py shouse_frozen_alt        the alt, always flipped left to right (Andrzej's rule)

Used when Claude makes the pictures in the Gemini app instead of through the API.
"""
import argparse
import glob
import os
import sys
import time

sys.path.insert(0, os.path.dirname(__file__))
import artlib  # noqa: E402

DOWNLOADS = os.path.join(os.path.expanduser('~'), 'Downloads')


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument('stem')
    ap.add_argument('--no-flip', action='store_true', help='keep an alt as Gemini made it')
    ap.add_argument('--wait', type=int, default=60, help='seconds to wait for the download')
    a = ap.parse_args()
    a.flip = a.stem.endswith('_alt') and not a.no_flip
    known = {s for h in artlib.homes() for s in (h.stem, h.alt_stem, h.alt2_stem)}
    if a.stem not in known:
        sys.exit(f'Unknown picture name: {a.stem}')
    files = []
    for _ in range(max(1, a.wait)):  # the download can take a while to land
        files = sorted((f for f in glob.glob(os.path.join(DOWNLOADS, 'Gemini_Generated_Image_*'))
                        if not f.endswith(('.crdownload', '.tmp'))), key=os.path.getmtime)
        good = []
        for f in files:  # an empty or half-written file (a misclicked download) is skipped
            try:
                from PIL import Image as _I
                with _I.open(f) as probe:
                    probe.load()
                good.append(f)
            except Exception:
                if os.path.getsize(f) == 0 and time.time() - os.path.getmtime(f) > 10:
                    os.remove(f)
        files = good
        if files:
            break
        # Sometimes the browser pane leaves a finished download as <guid>.tmp: take it once it
        # has stopped growing and opens as a picture.
        from PIL import Image
        for t in glob.glob(os.path.join(DOWNLOADS, '*.tmp')):
            try:
                if time.time() - os.path.getmtime(t) < 3:
                    continue
                with Image.open(t) as probe:
                    probe.load()
                fixed = os.path.join(DOWNLOADS, 'Gemini_Generated_Image_from_tmp_' + str(int(time.time())) + '.jpg')
                os.replace(t, fixed)
                files = [fixed]
                break
            except Exception:
                pass
        if files:
            break
        time.sleep(1)
    if not files:
        sys.exit('No Gemini picture in Downloads.')
    from PIL import Image, ImageOps
    src = files[-1]
    im = Image.open(src).convert('RGB')
    if a.flip:
        im = ImageOps.mirror(im)
    old = artlib.find(a.stem)
    if old:
        os.makedirs(artlib.REJECTED, exist_ok=True)
        n = 1
        while os.path.exists(os.path.join(artlib.REJECTED, f'{a.stem}_{n}.jpg')):
            n += 1
        os.replace(old, os.path.join(artlib.REJECTED, f'{a.stem}_{n}.jpg'))
    dst = os.path.join(artlib.ART, a.stem + '.jpg')
    im.save(dst, quality=95)
    os.remove(src)
    thumb = os.path.join(artlib.THUMBS, a.stem + '.jpg')
    if os.path.exists(thumb):
        os.remove(thumb)
    artlib.update(a.stem, status='new', comment='', fresh=False, flipped=a.flip,
                  event='made in the Gemini app' + (' (flipped)' if a.flip else ''))
    print(f'{os.path.basename(src)} -> {a.stem}.jpg {im.size}' + (' flipped' if a.flip else ''))


if __name__ == '__main__':
    main()
