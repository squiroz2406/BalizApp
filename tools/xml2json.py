import sys, json, re, glob, os
import xml.etree.ElementTree as ET
res = sys.argv[1]; out = sys.argv[2]
langs = {"es":"values","en":"values-en","it":"values-it","pt":"values-pt","de":"values-de"}
def unescape(s):
    s = s.replace("\\'", "'").replace('\\"', '"').replace("\\n", "\n").replace("\\@", "@").replace("\\?", "?")
    s = re.sub(r"%(\d)\$[sd]", lambda m: "{" + m.group(1) + "}", s)
    s = s.replace("%%", "%")
    return s
data = {}
plurals = {}
for lang, d in langs.items():
    data[lang] = {}; plurals[lang] = {}
    for f in sorted(glob.glob(os.path.join(res, d, "*.xml"))):
        if os.path.basename(f) in ("themes.xml","colors.xml"): continue
        root = ET.parse(f).getroot()
        for el in root:
            if el.get("translatable") == "false": continue
            if el.tag == "string":
                data[lang][el.get("name")] = unescape("".join(el.itertext()))
            elif el.tag == "plurals":
                plurals[lang][el.get("name")] = {i.get("quantity"): unescape("".join(i.itertext())) for i in el.findall("item")}
for lang in langs:
    json.dump({"strings": data[lang], "plurals": plurals[lang]}, open(os.path.join(out, lang + ".json"), "w"), ensure_ascii=False, indent=1)
    print(lang, len(data[lang]), len(plurals[lang]))
es = set(data["es"])
for lang in langs:
    miss = es - set(data[lang])
    if miss: print("missing in", lang, sorted(miss)[:5], len(miss))
