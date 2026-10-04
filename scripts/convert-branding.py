"""Convert the collected-V SVG to native Android vectors, using only Python's standard library.

Elliptical radial gradients are unit circles inside affine groups, not flattened bitmaps.
This deliberately supports the supplied SVG subset and rejects unsupported artwork.
"""
import argparse
import copy
from pathlib import Path
import re
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / "assets/branding/vnventory.svg"
DRAWABLES = ROOT / "app/src/main/res/drawable"
ANDROID = "http://schemas.android.com/apk/res/android"
AAPT = "http://schemas.android.com/aapt"
ET.register_namespace("android", ANDROID)
ET.register_namespace("aapt", AAPT)
CIRCLE = "M1,0A1,1 0,1 0,-1,0A1,1 0,1 0,1,0Z"


def tag(node):
    return node.tag.rsplit("}", 1)[-1]


def number(value):
    return f"{float(value):.10f}".rstrip("0").rstrip(".") or "0"


def element(tag_name, **attributes):
    return ET.Element(tag_name, {f"{{{ANDROID}}}{key}": str(value) for key, value in attributes.items()})


def rounded_rect(node, square=False):
    x, y = float(node.get("x", 0)), float(node.get("y", 0))
    w, h = float(node.get("width")), float(node.get("height"))
    rx = 0 if square else min(float(node.get("rx", 0)), w / 2)
    ry = 0 if square else min(float(node.get("ry", rx)), h / 2)
    n = number
    if not rx or not ry:
        return f"M{n(x)},{n(y)}H{n(x+w)}V{n(y+h)}H{n(x)}Z"
    return (f"M{n(x+rx)},{n(y)}H{n(x+w-rx)}A{n(rx)},{n(ry)} 0,0 1,{n(x+w)},{n(y+ry)}"
            f"V{n(y+h-ry)}A{n(rx)},{n(ry)} 0,0 1,{n(x+w-rx)},{n(y+h)}"
            f"H{n(x+rx)}A{n(rx)},{n(ry)} 0,0 1,{n(x)},{n(y+h-ry)}"
            f"V{n(y+ry)}A{n(rx)},{n(ry)} 0,0 1,{n(x+rx)},{n(y)}Z")


def transforms(parent, transform):
    consumed = ""
    for match in re.finditer(r"(translate|scale|rotate)\(([^)]+)\)", transform):
        consumed += match.group()
        values = [number(v) for v in re.split(r"[ ,]+", match.group(2).strip())]
        operation = match.group(1)
        if operation == "translate" and len(values) in (1, 2):
            group = element("group", translateX=values[0], translateY=values[1] if len(values) == 2 else "0")
        elif operation == "scale" and len(values) in (1, 2):
            group = element("group", scaleX=values[0], scaleY=values[-1])
        elif operation == "rotate" and len(values) in (1, 3):
            group = element("group", rotation=values[0], pivotX=values[1] if len(values) == 3 else "0", pivotY=values[2] if len(values) == 3 else "0")
        else:
            raise ValueError(f"Unsupported transform: {transform}")
        parent.append(group)
        parent = group
    if re.sub(r"\s+", "", consumed) != re.sub(r"\s+", "", transform):
        raise ValueError(f"Unsupported transform: {transform}")
    return parent


def reverse_closed_path(data):
    """Reverse the SVG's absolute M/L/Q contours for non-zero-winding holes.

    Geometry comes from the design source, never a separately maintained outline.
    Unsupported commands fail loudly rather than silently drifting from the SVG.
    """
    tokens = re.findall(r"[A-Za-z]|[-+]?(?:\d*\.\d+|\d+)(?:[eE][-+]?\d+)?", data)
    if re.sub(r"[\s,]+", "", data) != "".join(tokens):
        raise ValueError("Unsupported hole path syntax")
    index = 0
    command = None
    start = current = None
    segments = []
    while index < len(tokens):
        if tokens[index].isalpha():
            command = tokens[index]
            index += 1
        if command == "Z":
            if current != start:
                segments.append(("L", current, start, None))
            if index != len(tokens):
                raise ValueError("Hole must be one closed contour")
            break
        if command not in {"M", "L", "Q"}:
            raise ValueError("Hole paths support only absolute M/L/Q/Z")
        count = 4 if command == "Q" else 2
        values = tuple(number(value) for value in tokens[index:index + count])
        if len(values) != count:
            raise ValueError("Incomplete hole path")
        index += count
        end = values[-2:]
        if command == "M":
            if start is not None:
                raise ValueError("Hole must be one closed contour")
            start = current = end
            command = "L"
        else:
            if current is None:
                raise ValueError("Hole must start with M")
            segments.append((command, current, end, values[:2] if command == "Q" else None))
            current = end
    if command != "Z" or start is None:
        raise ValueError("Hole must be closed")
    result = "M" + ",".join(start)
    for kind, previous, end, control in reversed(segments):
        # A closing line returning to start is implicit in Z.
        if kind == "L" and previous == start:
            continue
        result += kind + (",".join(control) + " " if control else "") + ",".join(previous)
    return result + "Z"


class Converter:
    def __init__(self):
        self.svg = ET.parse(SOURCE).getroot()
        self.ids = {node.get("id"): node for node in self.svg.iter() if node.get("id")}
        if self.svg.get("viewBox") != "0 0 1024 1024":
            raise ValueError("Expected the original 1024-coordinate artwork")
        if any(tag(node) in {"image", "filter", "text", "use"} for node in self.svg.iter()):
            raise ValueError("The source must remain self-contained vector artwork")

    def gradient(self, path, property_name, source):
        attribute = ET.SubElement(path, f"{{{AAPT}}}attr", {"name": f"android:{property_name}"})
        if tag(source) == "linearGradient":
            if source.get("gradientUnits") != "userSpaceOnUse":
                raise ValueError("Linear gradients must have explicit source coordinates")
            gradient = element("gradient", type="linear", startX=source.get("x1"), startY=source.get("y1"),
                               endX=source.get("x2"), endY=source.get("y2"))
        else:
            gradient = element("gradient", type="radial", centerX="0", centerY="0", gradientRadius="1")
        attribute.append(gradient)
        for stop in source:
            color = stop.get("stop-color").removeprefix("#")
            alpha = round(float(stop.get("stop-opacity", 1)) * 255)
            gradient.append(element("item", offset=stop.get("offset"), color=f"#{alpha:02X}{color}"))

    def paint(self, path, kind, value, opacity=1):
        if value.startswith("url(#"):
            self.gradient(path, kind + "Color", self.ids[value[5:-1]])
        else:
            path.set(f"{{{ANDROID}}}{kind}Color", "#00000000" if value == "none" else value)
        if opacity != 1:
            path.set(f"{{{ANDROID}}}{kind}Alpha", number(opacity))

    def convert(self, node, parent, square=False):
        kind = tag(node)
        if node.get("transform"):
            parent = transforms(parent, node.get("transform"))
        if node.get("clip-path"):
            scope = element("group")
            parent.append(scope)
            parent = scope
            clip = self.ids[node.get("clip-path")[5:-1]]
            parent.append(element("clip-path", pathData=" ".join(child.get("d") for child in clip)))
        if kind == "g":
            scope = element("group", **({"name": node.get("id")} if node.get("id") else {}))
            parent.append(scope)
            for child in node:
                self.convert(child, scope, square)
            return
        if kind not in {"path", "rect", "ellipse"}:
            raise ValueError(f"Unsupported drawing element: {kind}")
        fill = node.get("fill", "#000000")
        gradient = self.ids.get(fill[5:-1]) if fill.startswith("url(#") else None
        if gradient is not None and tag(gradient) == "radialGradient":
            scope = element("group")
            parent.append(scope)
            if kind == "ellipse" and gradient.get("gradientUnits", "objectBoundingBox") == "objectBoundingBox":
                parent = transforms(scope, f'translate({node.get("cx")} {node.get("cy")}) scale({node.get("rx")} {node.get("ry")})')
            elif kind == "rect" and gradient.get("gradientUnits") == "userSpaceOnUse":
                scope.append(element("clip-path", pathData=rounded_rect(node, square)))
                parent = transforms(scope, gradient.get("gradientTransform", ""))
            else:
                raise ValueError("Unsupported radial-gradient geometry")
            path_data = CIRCLE
        else:
            if kind == "ellipse":
                raise ValueError("Only radial-gradient ellipses are used by this artwork")
            path_data = node.get("d") if kind == "path" else rounded_rect(node, square)
        path = element("path", pathData=path_data)
        parent.append(path)
        opacity = float(node.get("opacity", 1))
        self.paint(path, "fill", fill, opacity * float(node.get("fill-opacity", 1)))
        if node.get("stroke"):
            self.paint(path, "stroke", node.get("stroke"), opacity * float(node.get("stroke-opacity", 1)))
            for source, target in (("stroke-width", "strokeWidth"), ("stroke-linecap", "strokeLineCap"), ("stroke-linejoin", "strokeLineJoin")):
                if node.get(source):
                    path.set(f"{{{ANDROID}}}{target}", node.get(source))

    def vector(self, safe=False):
        root = element("vector", width="108dp", height="108dp", viewportWidth="108", viewportHeight="108")
        group = element("group", scaleX="0.10546875", scaleY="0.10546875")
        root.append(group)
        if safe:
            # AdaptiveIconDrawable enlarges its layers. Keep the sleeve tips within its safe circle.
            safe_group = element("group", scaleX="0.84", scaleY="0.84", pivotX="512", pivotY="512")
            group.append(safe_group)
            group = safe_group
        return root, group

    @staticmethod
    def xml(root):
        ET.indent(root, space="    ")
        return '<?xml version="1.0" encoding="utf-8"?>\n<!-- Generated from assets/branding/vnventory.svg; see scripts/convert-branding.py. -->\n' + ET.tostring(root, encoding="unicode") + "\n"

    def foreground(self, safe=False):
        root, group = self.vector(safe)
        self.convert(self.ids["collection-mark"], group)
        return self.xml(root)

    def background(self, launcher=False):
        root, group = self.vector()
        source = copy.deepcopy(self.ids["background-tile"])
        if launcher:
            source.remove(source[-1])  # The launcher supplies its own mask; avoid a nested tile rim.
        self.convert(source, group, square=launcher)
        return self.xml(root)

    def monochrome(self, safe=False):
        root, group = self.vector(safe)
        group = transforms(group, self.ids["collection-mark"].get("transform"))
        # Reverse-wound holes work in both native VectorDrawable and Compose, without evenOdd clips.
        label_hole = reverse_closed_path(self.ids["spine-label-face"].get("d"))
        tab_hole = reverse_closed_path(self.ids["tab-mark-face"].get("d"))
        group.append(element("clip-path", pathData="M0,0H1024V1024H0Z " + label_hole + " " + tab_hole))
        for name in ("left-game-sleeve", "right-game-sleeve"):
            path = self.ids[name].find("{http://www.w3.org/2000/svg}path")
            group.append(element("path", fillColor="#FFFFFF", pathData=path.get("d")))
        path = list(self.ids["gold-collection-tab"])[1]
        group.append(element("path", fillColor="#FFFFFF", pathData=path.get("d")))
        return self.xml(root)

    def files(self):
        return {
            "ic_launcher_foreground.xml": self.foreground(),
            "ic_launcher_foreground_safe.xml": self.foreground(safe=True),
            "ic_launcher_background.xml": self.background(launcher=True),
            "vnventory_logo_background.xml": self.background(),
            "ic_launcher_monochrome.xml": self.monochrome(safe=True),
            "vnventory_mark.xml": self.monochrome(),
        }


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--output-dir", type=Path, help="Generate vectors into this directory")
    parser.add_argument("--write", action="store_true", help="Write the generated Android vectors into the app drawable resources")
    parser.add_argument("--check", action="store_true", help="Verify committed vectors match the SVG")
    args = parser.parse_args()
    files = Converter().files()
    if args.write:
        for name, text in files.items():
            (DRAWABLES / name).write_text(text)
        print(f"Updated {len(files)} drawable resources in {DRAWABLES}")
    elif args.output_dir is not None:
        args.output_dir.mkdir(parents=True, exist_ok=True)
        for name, text in files.items():
            (args.output_dir / name).write_text(text)
        print(f"Generated {len(files)} branding drawables in {args.output_dir}")
    else:
        mismatches = [name for name, text in files.items() if not (DRAWABLES / name).is_file() or (DRAWABLES / name).read_text() != text]
        if mismatches:
            raise SystemExit("Vectors do not match the source: " + ", ".join(mismatches))
        print(f"Branding source/vector check passed: {len(files)} files")


if __name__ == "__main__":
    main()
