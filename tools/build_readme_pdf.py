"""Regenerate README.pdf from README.md.

Usage:  python tools/build_readme_pdf.py README.md README.pdf
Needs:  pip install markdown playwright && playwright install chromium; npm install -g @mermaid-js/mermaid-cli
"""
import base64, pathlib, re, subprocess, sys, tempfile
import markdown
from playwright.sync_api import sync_playwright

src, out = pathlib.Path(sys.argv[1]), pathlib.Path(sys.argv[2])
md = src.read_text(encoding="utf-8")
md = re.sub(r"^# .*\n", "", md, count=1)  # title lives on the cover

work = pathlib.Path(tempfile.mkdtemp())
cfg = work / "mermaid.json"
cfg.write_text('{"theme":"neutral","fontFamily":"Carlito, Calibri, sans-serif","flowchart":{"htmlLabels":true,"curve":"basis"},'
               '"sequence":{"useMaxWidth":true},"class":{"hideEmptyMembersBox":true},"themeVariables":{"fontSize":"13px"}}')
diagrams = []
def render(m):
    i = len(diagrams)
    (work / f"d{i}.mmd").write_text(m.group(1))
    subprocess.run(["mmdc", "-i", str(work / f"d{i}.mmd"), "-o", str(work / f"d{i}.svg"), "-c", str(cfg), "-b", "white"],
                   check=True, capture_output=True)
    svg = (work / f"d{i}.svg").read_text()
    diagrams.append(svg)
    return f"\n\nDIAGRAM{i}PLACEHOLDER\n\n"
md = re.sub(r"```mermaid\n(.*?)```", render, md, flags=re.S)
body = markdown.markdown(md, extensions=["tables", "fenced_code"]).replace("\\|", "|")
for i, svg in enumerate(diagrams):
    uri = "data:image/svg+xml;base64," + base64.b64encode(svg.encode()).decode()
    body = body.replace(f"<p>DIAGRAM{i}PLACEHOLDER</p>", f'<div class="diagram d{i}"><img src="{uri}"></div>')
# Two diagrams in a row (no text between them) are shown side by side, each at half width.
one = r'<div class="diagram d\d+"><img src="[^"]*"></div>'
body = re.sub(f'({one})\\s*({one})', r'<div class="pair">\1\2</div>', body)

css = """
@page { size: A4; margin: 14mm 15mm 14mm 15mm; }
body { font-family: Calibri, Carlito, sans-serif; font-size: 9.6pt; line-height: 1.28; color: #1f1f1f; margin: 0; }
.cover { height: 265mm; display: flex; flex-direction: column; justify-content: center; align-items: center; text-align: center; page-break-after: always; }
.cover h1 { font-size: 26pt; color: #1f4e79; margin: 0 0 14px; border: none; }
.cover .sub { font-size: 13pt; color: #2e74b5; line-height: 1.4; }
.cover hr { width: 70%; border: none; border-top: 1.5px solid #1f4e79; margin: 14px 0 50px; }
.cover .label { font-weight: bold; font-size: 12pt; margin: 6px 0; }
.cover .member { font-size: 12pt; margin: 6px 0; }
h2 { font-size: 13pt; color: #1f4e79; border-bottom: 1.2px solid #1f4e79; margin: 12px 0 5px; padding-bottom: 1px; page-break-after: avoid; }
h3 { font-size: 10.6pt; color: #2e74b5; margin: 8px 0 3px; page-break-after: avoid; }
p { margin: 3px 0 5px; }
ul { margin: 3px 0 5px; padding-left: 18px; } li { margin: 1px 0; }
code { font-family: Consolas, "DejaVu Sans Mono", monospace; font-size: 8.3pt; }
pre { background: #f3f5f8; border: 1px solid #d9dee5; padding: 5px 7px; margin: 4px 0; page-break-inside: avoid; }
pre code { font-size: 7.9pt; }
table { border-collapse: collapse; width: 100%; margin: 4px 0 6px; font-size: 8.4pt; line-height: 1.22; }
th { background: #dbe5f1; color: #1f3b5a; text-align: left; border: 1px solid #9fb3c8; padding: 2px 4px; }
td { border: 1px solid #c3cfdc; padding: 2px 4px; vertical-align: top; }
tr { page-break-inside: avoid; }
.diagram { text-align: center; margin: 4px 0 6px; page-break-inside: avoid; }
.diagram img { max-width: 100%; }
.d0 img { max-height: 105mm; } .d1 img { max-height: 80mm; }
.pair { display: flex; gap: 6mm; align-items: flex-start; page-break-inside: avoid; }
.pair .diagram { flex: 1; margin: 0; } .pair .diagram:first-child { flex: 1.9; } .pair .diagram img { width: 100%; height: auto; }
p:has(+ .pair) { page-break-after: avoid; break-after: avoid; }
p:has(+ .diagram) { page-break-after: avoid; break-after: avoid; }
"""
cover = """<div class="cover"><h1>SE301 Project 2026-27</h1>
<div class="sub">Singapore Vertical Farming Research Management System (SVFRMS)<br>Refactoring &amp; Modernisation: Technical Report</div>
<hr><div class="label">Group: 6</div><div class="label" style="margin-top:14px">Team members:</div>
<div class="member">Yuen Wai Yee</div><div class="member">Jaren Lim Haan</div><div class="member">Liew Tian Le</div></div>"""
html = f"<!doctype html><html><head><meta charset='utf-8'><title>SVFRMS Technical Report</title><style>{css}</style></head><body>{cover}{body}</body></html>"
(work / "report.html").write_text(html, encoding="utf-8")
with sync_playwright() as p:
    browser = p.chromium.launch()
    page = browser.new_page()
    page.goto((work / "report.html").as_uri())
    page.wait_for_load_state("networkidle")
    page.pdf(path=str(out), format="A4", print_background=True,
             margin={"top": "14mm", "bottom": "14mm", "left": "15mm", "right": "15mm"})
    browser.close()
print("html:", work / "report.html")
