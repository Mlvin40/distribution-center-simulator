# redxela-client

This template should help get you started developing with Vue 3 in Vite.

## Recommended IDE Setup

[VS Code](https://code.visualstudio.com/) + [Vue (Official)](https://marketplace.visualstudio.com/items?itemName=Vue.volar) (and disable Vetur).

## Recommended Browser Setup

- Chromium-based browsers (Chrome, Edge, Brave, etc.):
  - [Vue.js devtools](https://chromewebstore.google.com/detail/vuejs-devtools/nhdogjmejiglipccpnnnanhbledajbpd)
  - [Turn on Custom Object Formatter in Chrome DevTools](http://bit.ly/object-formatters)
- Firefox:
  - [Vue.js devtools](https://addons.mozilla.org/en-US/firefox/addon/vue-js-devtools/)
  - [Turn on Custom Object Formatter in Firefox DevTools](https://fxdx.dev/firefox-devtools-custom-object-formatters/)

## Customize configuration

See [Vite Configuration Reference](https://vite.dev/config/).

## Project Setup

El frontend utiliza Vue 3, Vite 8, Tailwind CSS 4 y Preline UI gratuito con JavaScript

Los estilos, variantes y el tema base de Preline se cargan en `src/assets/main.css`.
Las rutas CSS relativas corresponden al paquete Preline 4.2, que no exporta estos archivos como subrutas.
`src/router/index.js` inicializa Preline mediante `preline/non-auto` después de la navegación inicial y de cada navegación exitosa: espera `nextTick()`, ejecuta `cleanCollection()` y luego `autoInit()`.
Los componentes que agreguen contenido interactivo de forma asíncrona deberán inicializarlo después de actualizar el DOM y destruir sus instancias al desmontarse, según la documentación del componente.
No se cargan bibliotecas opcionales ni se ha generado un tema personalizado.

Referencia: [guía oficial de Preline para Vue](https://preline.co/docs/frameworks-vuejs.html).

```sh
npm install
```

### Compile and Hot-Reload for Development

```sh
npm run dev
```

### Compile and Minify for Production

```sh
npm run build
```

### Lint with [ESLint](https://eslint.org/)

```sh
npm run lint
```
