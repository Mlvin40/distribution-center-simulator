<script setup>
defineProps({
  name: { type: String, default: 'grid' },
  size: { type: [Number, String], default: 20 },
})
const paths = {
  grid: ['M3 3h7v7H3z M14 3h7v7h-7z M3 14h7v7H3z M14 14h7v7h-7z'],
  box: ['m12 3 9 5-9 5-9-5 9-5Z', 'M3 8v9l9 5 9-5V8 M12 13v9 M7.5 5.5l9 5'],
  orders: ['M8 4H5v17h14V4h-3', 'M8 2h8v5H8z M8 12h8 M8 16h5'],
  resources: [
    'M4 3h6v6H4z M14 3h6v6h-6z M4 15h6v6H4z M14 15h6v6h-6z M7 9v6 M17 9v6 M10 6h4 M10 18h4',
  ],
  chart: ['M4 3v17h17 M8 15l4-5 4 2 5-7'],
  activity: ['M3 12h4l3-8 4 16 3-8h4'],
  users: [
    'M16 21v-2a4 4 0 0 0-4-4H6a4 4 0 0 0-4 4v2 M16 4a4 4 0 0 1 0 8 M22 21v-2a4 4 0 0 0-3-3.87',
    'M13 7a4 4 0 1 1-8 0 4 4 0 0 1 8 0Z',
  ],
  clock: ['M12 8v4l3 2', 'M21 12a9 9 0 1 1-18 0 9 9 0 0 1 18 0Z'],
  check: ['m5 12 4 4L19 6'],
  shield: ['M12 3 3 7v6c0 4 9 8 9 8s9-4 9-8V7l-9-4Z', 'm8 12 3 3 5-6'],
  scan: ['M8 3H3v5 M16 3h5v5 M3 16v5h5 M21 16v5h-5 M7 8v8 M10 8v8 M14 8v8 M17 8v8 M2 12h20'],
  truck: [
    'M3 5h12v12H3z M15 10h4l3 4v3h-7',
    'M9 18a2 2 0 1 1-4 0 2 2 0 0 1 4 0Z M20 18a2 2 0 1 1-4 0 2 2 0 0 1 4 0Z',
  ],
  forklift: [
    'M3 15h9v-5H6V6H3v9Z M15 3v15h6 M19 3v11h3',
    'M8 18a2 2 0 1 1-4 0 2 2 0 0 1 4 0Z M14 18a2 2 0 1 1-4 0 2 2 0 0 1 4 0Z',
  ],
  play: ['m8 4 12 8-12 8V4Z'],
  pause: ['M8 4v16 M16 4v16'],
  stop: ['M5 5h14v14H5z'],
  plus: ['M12 5v14 M5 12h14'],
  close: ['m6 6 12 12 M18 6 6 18'],
  refresh: ['M20 7v5h-5 M4 17v-5h5', 'M5.1 7a8 8 0 0 1 13-2L20 8 M4 16l1.9 3a8 8 0 0 0 13-2'],
  server: ['M3 3h18v7H3z M3 14h18v7H3z M7 6h.01 M7 17h.01 M11 6h6 M11 17h6'],
  connection: [
    'M9 15 15 9',
    'M8 16l-1 1a4 4 0 0 1-6-6l4-4a4 4 0 0 1 6 0 M16 8l1-1a4 4 0 0 1 6 6l-4 4a4 4 0 0 1-6 0',
  ],
  arrow: ['M5 12h14 m-5-5 5 5-5 5'],
  chevron: ['m9 5 7 7-7 7'],
  menu: ['M4 6h16 M4 12h16 M4 18h16'],
  collapse: ['M3 4h18v16H3z M9 4v16 m7-11-3 3 3 3'],
  sun: [
    'M12 2v2 M12 20v2 M2 12h2 M20 12h2 M5 5l1.5 1.5 M17.5 17.5l1.5 1.5 M5 19l1.5-1.5 M17.5 6.5l1.5-1.5',
    'M16 12a4 4 0 1 1-8 0 4 4 0 0 1 8 0Z',
  ],
  moon: ['M20 15.5A9 9 0 0 1 8.5 4a9 9 0 1 0 11.5 11.5Z'],
  alert: ['m12 3 10 18H2L12 3Z M12 9v4 M12 17h.01'],
  info: ['M12 11v6 M12 7h.01', 'M21 12a9 9 0 1 1-18 0 9 9 0 0 1 18 0Z'],
  search: ['m21 21-5-5', 'M18 10a8 8 0 1 1-16 0 8 8 0 0 1 16 0Z'],
  code: ['m8 6-6 6 6 6 m8-12 6 6-6 6 M14 3l-4 18'],
  trash: ['M3 6h18 M9 6V3h6v3 M5 6l1 15h12l1-15 M10 10v7 M14 10v7'],
}
</script>
<template>
  <svg
    :width="size"
    :height="size"
    viewBox="0 0 24 24"
    fill="none"
    stroke="currentColor"
    stroke-width="1.7"
    stroke-linecap="round"
    stroke-linejoin="round"
    aria-hidden="true"
    class="shrink-0"
  >
    <path v-for="(path, index) in paths[name] || paths.grid" :key="index" :d="path" />
  </svg>
</template>
