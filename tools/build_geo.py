import json, struct, unicodedata

PROVINCIAS_AR = {
    # Codigos admin1 de GeoNames para Argentina: orden alfabetico con "Distrito Federal"
    # intercalado entre Corrientes y Entre Rios. Verificado abajo contra ciudades conocidas.
    "01": "Buenos Aires", "02": "Catamarca", "03": "Chaco", "04": "Chubut",
    "05": "Córdoba", "06": "Corrientes", "07": "Ciudad de Buenos Aires",
    "08": "Entre Ríos", "09": "Formosa", "10": "Jujuy", "11": "La Pampa",
    "12": "La Rioja", "13": "Mendoza", "14": "Misiones", "15": "Neuquén",
    "16": "Río Negro", "17": "Salta", "18": "San Juan", "19": "San Luis",
    "20": "Santa Cruz", "21": "Santa Fe", "22": "Santiago del Estero",
    "23": "Tierra del Fuego", "24": "Tucumán",
}

ciudades = json.load(open('cities.json'))
paises = {c['cca2']: c['translations'].get('spa', {}).get('common') or c['name']['common']
          for c in json.load(open('countries.json'))}

# --- verificacion del mapeo de provincias argentinas contra ciudades conocidas ---
CONTROL = {
    "Córdoba": "05", "Mendoza": "13", "Salta": "17", "Neuquén": "15",
    "Resistencia": "03", "Ushuaia": "23", "Rosario": "21", "La Plata": "01",
    "San Miguel de Tucumán": "24", "Posadas": "14", "Río Gallegos": "20",
    "Paraná": "08", "Corrientes": "06", "Formosa": "09", "Catamarca": "02",
    "San Salvador de Jujuy": "10", "Viedma": "16", "Rawson": "04",
    "La Rioja": "12", "San Juan": "18", "San Luis": "19",
    "Santiago del Estero": "22", "Santa Rosa": "11", "Buenos Aires": "07",
}
ar = [c for c in ciudades if c['country'] == 'AR']
por_nombre = {}
for c in ar:
    por_nombre.setdefault(c['name'], []).append(c['admin1'])
errores = []
for nombre, esperado in CONTROL.items():
    encontrados = por_nombre.get(nombre, [])
    if esperado not in encontrados:
        errores.append((nombre, esperado, encontrados))
if errores:
    print("FALLO la verificacion del mapeo de provincias:")
    for e in errores: print("  ", e)
    raise SystemExit(1)
print("OK: %d capitales y ciudades de control caen en la provincia esperada" % len(CONTROL))

# --- construccion del binario ---
regiones, indice_region = [], {}
def region_de(c):
    if c['country'] == 'AR':
        etiqueta = PROVINCIAS_AR.get(c['admin1'])
        if etiqueta is None:
            etiqueta = "Argentina"
    else:
        etiqueta = paises.get(c['country']) or c['country']
    if etiqueta not in indice_region:
        indice_region[etiqueta] = len(regiones)
        regiones.append(etiqueta)
    return indice_region[etiqueta]

filas = []
for c in ciudades:
    try:
        lat = int(round(float(c['lat']) * 100000))
        lon = int(round(float(c['lng']) * 100000))
    except (TypeError, ValueError):
        continue
    nombre = c['name'].strip()
    if not nombre:
        continue
    filas.append((lat, lon, nombre, region_de(c)))

filas.sort(key=lambda f: f[0])   # ordenadas por latitud: permite acotar la busqueda

nombres_blob = bytearray()
offsets = []
for lat, lon, nombre, reg in filas:
    offsets.append(len(nombres_blob))
    b = nombre.encode('utf-8')
    nombres_blob.append(len(b))
    nombres_blob += b

regiones_blob = bytearray()
regiones_offsets = []
for r in regiones:
    regiones_offsets.append(len(regiones_blob))
    b = r.encode('utf-8')
    regiones_blob.append(len(b))
    regiones_blob += b

with open('ciudades.bin', 'wb') as f:
    f.write(b'PFGEO1')
    f.write(struct.pack('>ii', len(filas), len(regiones)))
    for lat, lon, _, _ in filas: f.write(struct.pack('>ii', lat, lon))
    for off in offsets: f.write(struct.pack('>i', off))
    for _, _, _, reg in filas: f.write(struct.pack('>H', reg))
    f.write(struct.pack('>i', len(nombres_blob)))
    f.write(nombres_blob)
    for off in regiones_offsets: f.write(struct.pack('>i', off))
    f.write(struct.pack('>i', len(regiones_blob)))
    f.write(regiones_blob)

import os
print("ciudades: %d | regiones: %d | archivo: %.2f MB" % (len(filas), len(regiones), os.path.getsize('ciudades.bin')/1048576))
