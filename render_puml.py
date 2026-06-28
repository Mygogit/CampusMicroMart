"""Render PlantUML to PNG/SVG via public server."""
import zlib, urllib.request, binascii, os

with open(r'E:\数据\CampusMicroMart\系统用例图.puml', 'r', encoding='utf-8') as f:
    source = f.read()

compressed = zlib.compress(source.encode('utf-8'), level=9)[2:-4]
hex_str = '~1' + binascii.hexlify(compressed).decode()

# Try SVG
svg_url = f'https://www.plantuml.com/plantuml/svg/{hex_str}'
try:
    req = urllib.request.Request(svg_url, headers={'User-Agent': 'Mozilla/5.0'})
    with urllib.request.urlopen(req, timeout=30) as resp:
        data = resp.read()
        if data.startswith(b'<?xml') or data.startswith(b'<svg'):
            with open(r'E:\数据\CampusMicroMart\系统用例图.svg', 'wb') as f:
                f.write(data)
            print(f'OK SVG: {len(data)} bytes')
        else:
            print(f'UNEXPECTED: {data[:200]}')
except Exception as e:
    print(f'Error SVG: {e}')

# Try PNG
png_url = f'https://www.plantuml.com/plantuml/png/{hex_str}'
try:
    req = urllib.request.Request(png_url, headers={'User-Agent': 'Mozilla/5.0'})
    with urllib.request.urlopen(req, timeout=30) as resp:
        data = resp.read()
        if data[:4] == b'\x89PNG':
            with open(r'E:\数据\CampusMicroMart\系统用例图.png', 'wb') as f:
                f.write(data)
            print(f'OK PNG: {len(data)} bytes')
        else:
            print(f'Not PNG: {data[:100]}')
except Exception as e:
    print(f'Error PNG: {e}')
