# Guía del piloto en un despacho

Guía para instalar File Search en un despacho, acompañarlo durante la prueba y recoger lo necesario para decidir si el producto está listo para venderse.

## 1. Antes de ir

- Descarga el instalador `FileSearch-Setup-<versión>.exe` del artifact `windows-installer` de la última ejecución de CI en `main`.
- Genera una licencia de piloto con fecha de fin (por ejemplo, 60 días) para no depender de la prueba de 30 días:
  ```bash
  cd tfg-filesearch-gui
  npm run license -- issue --private <tu-clave>.pem --customer "Despacho X" --seats 2 --expires 2026-12-31 --out despacho-x.lic
  ```
- Acuerda con el despacho una carpeta real de documentos (idealmente la de un área concreta, de 5.000 a 50.000 archivos) y un ordenador con Windows 10 u 11.

## 2. Qué decir al despacho sobre sus datos

- La aplicación lee e indexa los documentos **solo en ese ordenador**. No envía nada a internet ni a ningún servidor, tampoco al proveedor.
- El índice se guarda en `C:\Users\<usuario>\.filesearch`. Contiene el texto de los documentos, así que tiene la misma sensibilidad que la carpeta original y debe estar en un equipo con el disco cifrado (BitLocker).
- La aplicación nunca modifica, mueve ni borra los documentos; solo los lee.
- Detecta DNI, NIE, CIF, IBAN, teléfonos, correos, números de procedimiento y palabras relacionadas con la salud con reglas fijas, en el propio ordenador. No usa inteligencia artificial ni servicios externos.
- Desinstalar la aplicación no borra el índice; se puede borrar a mano con la carpeta anterior.

## 3. Instalación (unos 5 minutos)

1. Ejecuta el instalador. No necesita permisos de administrador.
2. Como el instalador aún no está firmado, Windows puede mostrar «Windows protegió su PC». Pulsa **Más información** y después **Ejecutar de todas formas**.
3. Abre File Search, ve a **Configuración > Licencia > Instalar licencia…** y elige el archivo `.lic`.
4. En **Configuración > Documentos**, pulsa **Elegir carpeta**, selecciona la carpeta acordada y pulsa **Actualizar ahora**.
5. La primera indexación puede tardar: los PDF escaneados pasan por OCR y son lo más lento. Se puede seguir trabajando mientras tanto.
6. Al terminar, revisa el resumen: si hay archivos que no se han podido leer, apunta los motivos.

## 4. Qué probar con el abogado

Pide al abogado que busque cosas que busca de verdad en su día a día y anota el resultado de cada una:

| Prueba | Ejemplo | ¿Lo encuentra? |
|---|---|---|
| Palabra del contenido, con variantes | `desahucio` encuentra «desahucios» | |
| Frase exacta | `"cláusula suelo"` | |
| Sin tildes ni mayúsculas | `clausula suelo` | |
| Nombre de un cliente o contrario | apellido del cliente | |
| Correo de Outlook y sus adjuntos | asunto o texto de un adjunto | |
| Escrito escaneado | una palabra de una sentencia escaneada | |
| Documento dentro de un ZIP | nombre de un documento del ZIP | |
| Vista previa | clic en un resultado; Intro y Mayús+Intro para saltar entre coincidencias | |
| Abrir el original | botón **Abrir** en la vista previa | |
| Documentos de un cliente por su DNI | **Filtros > Contiene el dato**: el DNI del cliente, escrito como sea | |
| Un procedimiento | **Filtros > Contiene el dato**: `456/2024` | |
| Documentos con datos de salud | **Filtros > Datos de salud** | |
| Solicitud RGPD | **Informe RGPD** con el nombre y el DNI de un cliente; guardar el PDF | |

## 5. Durante el piloto

- Pasados unos días, pregunta si la aplicación se actualiza sola al abrirla (lo hace) y si ha encontrado algo que antes costaba encontrar.
- Si algo falla, pide que vaya a **Configuración > Soporte > Copiar información** y te pegue el texto. No incluye nombres ni contenido de documentos.

## 6. Qué preguntar al terminar

1. ¿Cuántas veces a la semana lo has usado?
2. ¿Qué búsqueda no encontró lo que esperabas?
3. ¿Cuánto tiempo te ahorra frente a buscar en las carpetas de Windows o en Outlook?
4. ¿Qué te falta para usarlo a diario?
5. ¿Pagarías por ello? ¿Cuánto al mes por ordenador?

Con las respuestas a las preguntas 2 y 4 se decide el siguiente hito.
