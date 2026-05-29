export function wrapHtml(fragment) {
  return `<!DOCTYPE html>
<html>
<head>
  <meta charset="UTF-8"/>
  <style>
    body { font-family: Arial, sans-serif; margin: 32px; color: #222; line-height: 1.6; }
    h1, h2 { margin-bottom: 12px; }
    ul, ol { padding-left: 20px; }
    blockquote { border-left: 3px solid #cbd5e1; margin: 0 0 0 0; padding-left: 14px; color: #64748b; }
    hr { border: none; border-top: 1px solid #e2e8f0; margin: 16px 0; }
  </style>
</head>
<body>
${fragment}
</body>
</html>`
}

export function extractBody(html) {
  if (!html) return ''
  const doc = new DOMParser().parseFromString(html, 'text/html')
  return doc.body.innerHTML
}
