<script setup>
import { computed, onMounted, onUnmounted, ref, useId } from 'vue'
const props = defineProps({
  title: String,
  caption: { type: String, default: 'Segundos simulados · orden de atención' },
  points: { type: Array, required: true },
})
const figure = ref(null)
const width = ref(900)
let observer
onMounted(() => {
  observer = new ResizeObserver(([entry]) => {
    width.value = Math.max(280, entry.contentRect.width)
  })
  observer.observe(figure.value)
})
onUnmounted(() => observer?.disconnect())
const gradient = useId().replaceAll(':', '')
const max = computed(() => Math.max(1, ...props.points.map((point) => point.value)))
const plotted = computed(() =>
  props.points.map((point, index) => ({
    ...point,
    x:
      54 +
      (props.points.length === 1
        ? (width.value - 74) / 2
        : (index / (props.points.length - 1)) * (width.value - 74)),
    y: 230 - (point.value / max.value) * 190,
  })),
)
const line = computed(() =>
  plotted.value.map((point, index) => `${index ? 'L' : 'M'} ${point.x},${point.y}`).join(' '),
)
const area = computed(() =>
  plotted.value.length
    ? `${line.value} L ${plotted.value.at(-1).x},230 L ${plotted.value[0].x},230 Z`
    : '',
)
</script>
<template>
  <figure ref="figure" class="px-3 pt-3 pb-2 sm:px-5">
    <figcaption
      class="mb-2 flex flex-wrap items-center justify-between gap-2 text-xs text-secondary"
    >
      <span>{{ title }}</span
      ><span>{{ caption }}</span>
    </figcaption>
    <svg
      :viewBox="`0 0 ${width} 265`"
      class="block h-60 w-full sm:h-72"
      role="img"
      :aria-label="title"
    >
      <defs>
        <linearGradient :id="gradient" x1="0" y1="0" x2="0" y2="1">
          <stop offset="0%" stop-color="#16a876" stop-opacity=".28" />
          <stop offset="100%" stop-color="#16a876" stop-opacity=".02" />
        </linearGradient>
      </defs>
      <g v-for="tick in [0, 1, 2, 3, 4]" :key="tick">
        <line
          x1="54"
          :x2="width - 20"
          :y1="40 + tick * 47.5"
          :y2="40 + tick * 47.5"
          stroke="var(--color-border)"
          stroke-width=".7"
        />
        <text
          x="42"
          :y="44 + tick * 47.5"
          text-anchor="end"
          fill="var(--color-muted)"
          font-size="10"
        >
          {{ Math.round(max * (1 - tick / 4)) }}
        </text>
      </g>
      <path :d="area" :fill="`url(#${gradient})`" />
      <path
        :d="line"
        fill="none"
        stroke="#16a876"
        stroke-width="2.2"
        stroke-linecap="round"
        stroke-linejoin="round"
      />
      <g v-for="(point, index) in plotted" :key="point.label">
        <circle
          :cx="point.x"
          :cy="point.y"
          r="3"
          fill="var(--color-surface)"
          stroke="#16a876"
          stroke-width="1.5"
          tabindex="0"
          :aria-label="`${point.label}: ${point.value.toFixed(1)} segundos simulados de espera`"
        >
          <title>{{ point.label }} · {{ point.value.toFixed(1) }} s simulados</title>
        </circle>
        <text
          v-if="
            index === 0 || index === plotted.length - 1 || index === Math.floor(plotted.length / 2)
          "
          :x="point.x"
          y="252"
          text-anchor="middle"
          fill="var(--color-muted)"
          font-size="10"
        >
          {{ index + 1 }}
        </text>
      </g>
    </svg>
  </figure>
</template>
