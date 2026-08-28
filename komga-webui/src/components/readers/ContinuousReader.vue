<template>
  <div class="continuous-reader" @mousedown="onMouseDown">
    <div :class="`d-flex flex-column px-0 mx-0` "
         v-scroll="onScroll"
    >
      <img v-for="(page, i) in pages"
           :key="`page${i}`"
           :alt="`Page ${page.number}`"
           :src="shouldLoad(i) ? page.url : undefined"
           :height="calcHeight(page)"
           :width="calcWidth(page)"
           :id="`page${page.number}`"
           :style="`margin: ${i === 0 ? 0 : pageMargin}px auto;`"
           v-intersect="onIntersect"
      />
    </div>

    <!--  clickable zone: top  -->
    <div @click="onClickTop()"
         class="top-quarter"
         style="z-index: 1;"
    />

    <!--  clickable zone: bottom  -->
    <div @click="onClickBottom()"
         class="bottom-quarter"
         style="z-index: 1;"
    />

    <!--  clickable zone: menu  -->
    <div @click="onClickCenter()"
         class="center-vertical"
         style="z-index: 1;"
    />
  </div>
</template>

<script lang="ts">
import Vue from 'vue'
import {ContinuousScaleType} from '@/types/enum-reader'
import {PageDtoWithUrl} from '@/types/komga-books'
import {throttle} from 'lodash'

export default Vue.extend({
  name: 'ContinuousReader',
  data: () => {
    return {
      offsetTop: 0,
      totalHeight: 1000,
      currentPage: 1,
      seen: [] as boolean[],
      isMouseDown: false,
      hasDragged: false,
      dragStartX: 0,
      dragStartY: 0,
      dragStartScrollTop: 0,
      dragStartScrollLeft: 0,
      dragThreshold: 5,
    }
  },
  props: {
    pages: {
      type: Array as () => PageDtoWithUrl[],
      required: true,
    },
    animations: {
      type: Boolean,
      required: true,
    },
    page: {
      type: Number,
      required: true,
    },
    scale: {
      type: String as () => ContinuousScaleType,
      required: true,
    },
    sidePadding: {
      type: Number,
      required: true,
    },
    pageMargin: {
      type: Number,
      required: true,
    },
  },
  watch: {
    pages: {
      handler(val) {
        this.seen = new Array(val.length).fill(false)
        if (this.page === 1) window.scrollTo(0, 0)
      },
      immediate: true,
    },
    page: {
      handler(val) {
        if (val != this.currentPage) {
          this.$vuetify.goTo(`#page${val}`, {
            duration: 0,
          })
        }
      },
      immediate: false,
    },
  },
  created() {
    window.addEventListener('keydown', this.keyPressed)
  },
  destroyed() {
    window.removeEventListener('keydown', this.keyPressed)
    window.removeEventListener('mousemove', this.onMouseMove)
    window.removeEventListener('mouseup', this.onMouseUp)
  },
  mounted() {
    if (this.page != this.currentPage) {
      this.$vuetify.goTo(`#page${this.page}`, {
        duration: 0,
      })
    }
  },
  computed: {
    canPrev(): boolean {
      return this.offsetTop > 0
    },
    canNext(): boolean {
      return this.offsetTop + this.$vuetify.breakpoint.height < this.totalHeight
    },
    goToOptions(): object | undefined {
      if (this.animations) return undefined
      return {duration: 0}
    },
    totalSidePadding(): number {
      return this.sidePadding * 2
    },
  },
  methods: {
    keyPressed: throttle(function (this: any, e: KeyboardEvent) {
      switch (e.key) {
        case ' ':
        case 'PageDown':
        case 'ArrowDown':
          if (!this.canNext) this.$emit('jump-next')
          break
        case 'PageUp':
        case 'ArrowUp':
          if (!this.canPrev) this.$emit('jump-previous')
          break
      }
    }, 500),
    onScroll(e: any) {
      this.offsetTop = e.target.scrollingElement.scrollTop
      this.totalHeight = e.target.scrollingElement.scrollHeight
    },
    onIntersect(entries: any) {
      if (entries[0].isIntersecting) {
        const page = parseInt(entries[0].target.id.replace('page', ''))
        this.seen.splice(page - 1, 1, true)
        this.currentPage = page
        this.$emit('update:page', page)
      }
    },
    shouldLoad(page: number): boolean {
      return page == 0 || this.seen[page] || Math.abs((this.currentPage - 1) - page) <= 2
    },
    calcHeight(page: PageDtoWithUrl): number | undefined {
      switch (this.scale) {
        case ContinuousScaleType.WIDTH:
          if (page.height && page.width)
            return page.height / (page.width / (this.$vuetify.breakpoint.width - (this.$vuetify.breakpoint.width * this.totalSidePadding) / 100))
          return undefined
        case ContinuousScaleType.ORIGINAL:
          return page.height || undefined
        default:
          return undefined
      }
    },
    calcWidth(page: PageDtoWithUrl): number | undefined {
      switch (this.scale) {
        case ContinuousScaleType.WIDTH:
          return this.$vuetify.breakpoint.width - (this.$vuetify.breakpoint.width * this.totalSidePadding) / 100
        case ContinuousScaleType.ORIGINAL:
          return page.width || undefined
        default:
          return undefined
      }
    },
    onMouseDown(e: MouseEvent) {
      if (e.button !== 0) return
      this.isMouseDown = true
      this.hasDragged = false
      this.dragStartX = e.clientX
      this.dragStartY = e.clientY
      this.dragStartScrollTop = window.scrollY || window.pageYOffset || document.documentElement.scrollTop
      this.dragStartScrollLeft = window.scrollX || window.pageXOffset || document.documentElement.scrollLeft
      window.addEventListener('mousemove', this.onMouseMove)
      window.addEventListener('mouseup', this.onMouseUp)
    },
    onMouseMove(e: MouseEvent) {
      if (!this.isMouseDown) return
      const deltaX = e.clientX - this.dragStartX
      const deltaY = e.clientY - this.dragStartY
      if (!this.hasDragged) {
        if (Math.hypot(deltaX, deltaY) > this.dragThreshold) {
          this.hasDragged = true
        }
      }
      if (this.hasDragged) {
        window.scrollTo(this.dragStartScrollLeft - deltaX, this.dragStartScrollTop - deltaY)
        e.preventDefault()
      }
    },
    onMouseUp(e: MouseEvent) {
      if (!this.isMouseDown) return
      this.isMouseDown = false
      window.removeEventListener('mousemove', this.onMouseMove)
      window.removeEventListener('mouseup', this.onMouseUp)
      if (this.hasDragged) {
        const suppressClick = (ev: MouseEvent) => {
          ev.stopPropagation()
          ev.preventDefault()
          window.removeEventListener('click', suppressClick, true)
        }
        window.addEventListener('click', suppressClick, true)
        setTimeout(() => {
          window.removeEventListener('click', suppressClick, true)
          this.hasDragged = false
        }, 50)
      }
    },
    onClickCenter() {
      if (this.hasDragged) return
      this.centerClick()
    },
    onClickTop() {
      if (this.hasDragged) return
      this.prev()
    },
    onClickBottom() {
      if (this.hasDragged) return
      this.next()
    },
    centerClick() {
      this.$emit('menu')
    },
    prev() {
      if (this.canPrev) {
        const step = this.$vuetify.breakpoint.height * 0.95
        this.$vuetify.goTo(this.offsetTop - step, this.goToOptions)
      } else {
        this.$emit('jump-previous')
      }
    },
    next() {
      if (this.canNext) {
        const step = this.$vuetify.breakpoint.height * 0.95
        this.$vuetify.goTo(this.offsetTop + step, this.goToOptions)
      } else {
        this.$emit('jump-next')
      }
    },
  },
})
</script>
<style scoped>
.continuous-reader {
  user-select: none;
  -webkit-user-select: none;
}

.continuous-reader img {
  user-select: none;
  -webkit-user-select: none;
  -webkit-user-drag: none;
}

.top-quarter {
  top: 0;
  height: 25vh;
  width: 100%;
  position: fixed;
}

.bottom-quarter {
  top: 75vh;
  height: 25vh;
  width: 100%;
  position: fixed;
}

.center-vertical {
  top: 25vh;
  height: 50vh;
  width: 100%;
  position: fixed;
}
</style>
