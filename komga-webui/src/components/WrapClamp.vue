<template>
  <div class="wrap-clamp">
    <div ref="container" class="wrap-clamp__container">
      <span
        v-for="(item, i) in items"
        :key="itemKey && item != null ? item[itemKey] : i"
        ref="items"
        class="wrap-clamp__item"
        :style="itemStyle(i)"
      >
        <slot name="item" :item="item" :index="i"/>
      </span>
    </div>
    <slot
      name="after"
      :clamped="clamped"
      :expanded="expanded"
      :hidden-items="hiddenItems"
      :toggle="toggle"
    />
  </div>
</template>

<script lang="ts">
import Vue from 'vue'
import {debounce} from 'lodash'

/**
 * WrapClamp
 *
 * Vue 2 / Vuetify 2 equivalent of the <WrapClamp> component used in next-ui
 * (which relies on the `vue-clamp` package, itself requiring Vue >=3.5).
 *
 * It renders a list of atomic items (e.g. chips) in a wrapping flex container,
 * measures the real rendered layout, and clamps the display to `maxLines`
 * lines, exposing a `+{count} more` / `Show less` toggle via the `after` slot.
 *
 * This does not depend on `vue-clamp`: it only relies on standard DOM
 * measurement APIs (offsetTop / ResizeObserver), which are available
 * regardless of the Vue major version.
 */
export default Vue.extend({
  name: 'WrapClamp',
  props: {
    items: {
      type: Array,
      default: () => [],
    },
    maxLines: {
      type: Number,
      default: 2,
    },
    /**
     * Optional property name to use as the v-for key when items are objects.
     * When not provided, the item index is used.
     */
    itemKey: {
      type: String,
      default: undefined,
    },
  },
  data: () => ({
    expanded: false,
    clamped: false,
    cutoffIndex: 0,
    resizeObserver: undefined as ResizeObserver | undefined,
    measureDebounced: undefined as (() => void) | undefined,
  }),
  computed: {
    hiddenItems(): any[] {
      return this.items.slice(this.cutoffIndex)
    },
  },
  watch: {
    items() {
      this.expanded = false
      this.measureDebounced?.()
    },
    maxLines() {
      this.measureDebounced?.()
    },
  },
  mounted() {
    this.measureDebounced = debounce(this.measure, 50)
    this.measure()

    if (typeof ResizeObserver !== 'undefined') {
      this.resizeObserver = new ResizeObserver(() => this.measureDebounced?.())
      this.resizeObserver.observe(this.$refs.container as Element)
    } else {
      window.addEventListener('resize', this.measureDebounced)
    }
  },
  beforeDestroy() {
    if (this.resizeObserver) {
      this.resizeObserver.disconnect()
    } else if (this.measureDebounced) {
      window.removeEventListener('resize', this.measureDebounced)
    }
  },
  methods: {
    toggle() {
      this.expanded = !this.expanded
    },
    itemStyle(index: number): object {
      if (this.expanded || !this.clamped || index < this.cutoffIndex) return {}
      return {display: 'none'}
    },
    measure() {
      this.$nextTick(() => {
        const itemEls = (this.$refs.items || []) as HTMLElement[]
        if (itemEls.length === 0) {
          this.clamped = false
          this.cutoffIndex = this.items.length
          return
        }

        // Temporarily force every item visible so we can measure the
        // layout as if nothing was clamped yet. The reactive :style
        // binding (itemStyle) will re-apply the correct display value
        // right after, once cutoffIndex/clamped are updated below.
        const previousDisplay: string[] = itemEls.map((el) => el.style.display)
        itemEls.forEach((el) => {
          el.style.display = ''
        })

        const tops = itemEls.map((el) => el.offsetTop)
        const uniqueTops = [...new Set(tops)].sort((a, b) => a - b)

        if (uniqueTops.length <= this.maxLines) {
          this.clamped = false
          this.cutoffIndex = this.items.length
        } else {
          this.clamped = true
          const cutoffTop = uniqueTops[this.maxLines]
          let idx = tops.findIndex((t) => t >= cutoffTop)
          if (idx === -1) idx = this.items.length
          // Always keep at least one item visible.
          this.cutoffIndex = Math.max(idx, 1)
        }

        itemEls.forEach((el, i) => {
          el.style.display = previousDisplay[i]
        })
      })
    },
  },
})
</script>

<style scoped>
.wrap-clamp__container {
  display: flex;
  flex-wrap: wrap;
}

.wrap-clamp__item {
  display: inline-flex;
  max-width: 100%;
}
</style>
