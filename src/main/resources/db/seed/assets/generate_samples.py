"""Regenerate the clearly fictional PDFs used by the enrollment demo."""

from pathlib import Path

from reportlab.lib.colors import HexColor
from reportlab.lib.pagesizes import A4
from reportlab.pdfgen.canvas import Canvas


ROOT = Path(__file__).resolve().parent
SAMPLES = {
    "dni-muestra.pdf": (
        "Documento Nacional de Identidad (DNI)",
        ["Frente: espacio de muestra para datos identificatorios.",
         "Dorso: espacio de muestra para domicilio y datos complementarios.",
         "Este PDF representa una copia completa y legible para el circuito de inscripción."],
    ),
    "dni-observado-muestra.pdf": (
        "Documento Nacional de Identidad (DNI)",
        ["Frente: copia de muestra.",
         "DORSO FALTANTE: ejemplo para observar la entrega y solicitar su reemplazo."],
    ),
    "certificado-aptitud-fisica-muestra.pdf": (
        "Certificado de aptitud física",
        ["Documento complementario para confirmar la inscripción.",
         "Ejemplo de entrega diferida dentro del circuito administrativo.",
         "No acredita aptitud física ni contiene una evaluación médica."],
    ),
    "partida-nacimiento-muestra.pdf": (
        "Partida de nacimiento",
        ["Documento de muestra para respaldar una solicitud de vinculación.",
         "Permite demostrar su descarga y revisión por la institución."],
    ),
}


for filename, (title, lines) in SAMPLES.items():
    canvas = Canvas(str(ROOT / filename), pagesize=A4, invariant=True)
    canvas.setTitle(f"{title} — muestra ficticia Bemol")
    canvas.setAuthor("Bemol — datos de demostración")
    canvas.setFillColor(HexColor("#172c43"))
    canvas.rect(0, 720, A4[0], 122, fill=1, stroke=0)
    canvas.setFillColor(HexColor("#ffffff"))
    canvas.setFont("Helvetica-Bold", 17)
    canvas.drawString(40, 782, "BEMOL · DOCUMENTACIÓN DE DEMOSTRACIÓN")
    canvas.setFont("Helvetica", 13)
    canvas.drawString(40, 751, title)
    canvas.setFillColor(HexColor("#172c43"))
    canvas.setFont("Helvetica-Bold", 14)
    canvas.drawString(40, 670, "MUESTRA FICTICIA — SIN VALIDEZ")
    canvas.setFont("Helvetica", 11)
    for index, line in enumerate(lines):
        canvas.drawString(40, 620 - index * 28, line)
    canvas.setFont("Helvetica-Oblique", 10)
    canvas.drawString(40, 100, "No es un documento oficial. No contiene datos personales reales ni firmas.")
    canvas.drawString(40, 80, "Uso exclusivo en demostraciones y ambientes de prueba de Bemol.")
    canvas.save()
