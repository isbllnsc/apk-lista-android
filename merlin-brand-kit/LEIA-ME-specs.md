# Merlin — Automation Wizard · Kit de Marca

Guia rápido de implementação para o site. O guia visual completo está no link do artifact (veja com quem enviou).

---

## Identidade verbal
- **Nome:** Merlin
- **Subtítulo:** Automation Wizard
- **Frase de apoio:** *"Mensagens, interações e alcance nas redes sociais — num passe de mágica."*

---

## Cores (paleta principal)

| Cor | HEX | Uso |
|---|---|---|
| Violeta Elétrico | `#8B5CF6` | Primária — marca, botões/CTAs |
| Índigo Arcano | `#4C2A85` | Profundidade, gradientes |
| Ciano de Circuito | `#28E0E8` | Acento tech — links, dados, destaques |
| Ouro Encantado | `#F6B93B` | Faíscas, destaques pontuais |
| Noite Arcana | `#130B2B` | Fundo principal (dark) |
| Névoa | `#EAE6F7` | Texto sobre fundo escuro |

**Tons usados no pixel art da marca (chapéu/wordmark):**
contorno `#120826` · roxo claro `#9A6BFF` · roxo médio `#5B3FA8` · sombra `#3F2A72` · ouro `#FFCF4A` / `#D99A2C` · ciano `#28E0E8` · branco `#F4F0FF`

Gradiente do wordmark: `#EAE6F7` → `#A78BFA` → `#28E0E8` (esquerda → direita).

---

## Tipografia

| Papel | Fonte | Onde baixar (grátis) |
|---|---|---|
| Títulos / Display | **Clash Display** (600–800) | fontshare.com |
| Corpo / Interface | **Satoshi** (400–500) | fontshare.com |
| Mono / dados / subtítulo | **JetBrains Mono** | fonts.google.com (OFL) |

> ⚠️ O wordmark **"MERLIN" é pixel art custom — NÃO é uma fonte.** Use sempre o arquivo `merlin-wordmark.svg` (não redigite com fonte). O "AUTOMATION WIZARD" usa JetBrains Mono em caixa alta com tracking largo.

---

## Arquivos do kit

| Arquivo | Quando usar |
|---|---|
| `merlin-mark.svg` | Só o chapéu (marca). **Preferir SVG sempre** — escala nítida |
| `merlin-wordmark.svg` | Só a palavra MERLIN em pixel |
| `merlin-lockup-horizontal.svg` | Logo completo horizontal (topo de site, header) |
| `merlin-lockup-vertical.svg` | Logo completo vertical (rodapé, cards, splash) |
| `merlin-mark-512.png` | Chapéu em PNG transparente (quando não der pra usar SVG) |
| `favicon-256.png` / `-64` / `-32` | Favicon do site (chapéu centralizado, quadrado) |
| `merl-mascote.png` | Mascote completo (ilustrações, redes, onboarding) |

---

## Notas de implementação (web)

- **Use os SVGs** no site — são vetoriais e ficam nítidos em qualquer tamanho (o pixel art é preservado com `shape-rendering: crispEdges`, já embutido).
- Se precisar **escalar um PNG pixelado** via CSS, use `image-rendering: pixelated;` pra não borrar.
- **Favicon:** `merlin-mark.svg` (moderno) com fallback `favicon-256.png`.
- Fundo ideal da marca: **escuro** (`#130B2B` ou neutro escuro). Sobre claro também funciona (o contorno escuro do pixel aparece).

## Regras de uso
- ✅ Mantenha um espaço livre igual à altura da aba do chapéu ao redor da marca.
- ✅ Mantenha os pixels **nítidos** e a faísca dourada da faixa.
- ❌ Não distorça, gire nem suavize/borre os pixels.
- ❌ Não recolora o gradiente do wordmark nem troque as cores do chapéu.
