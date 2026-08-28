<template>
  <div
    class="paged-reader"
    @mousedown="onMouseDown"
    v-touch="{
               left: () => {if(swipe) {turnRight()}},
               right: () => {if(swipe) {turnLeft()}},
               up: () => {if(swipe) {verticalNext()}},
               down: () => {if(swipe) {verticalPrev()}}
             }"
  >
    <v-carousel v-model="carouselPage"
                :show-arrows="false"
                :continuous="false"
                :reverse="flipDirection"
                :vertical="vertical"
                hide-delimiters
                touchless
                height="100%"
    >
      <!--  Carousel: pages  -->
      <v-carousel-item v-for="(spread, i) in spreads"
                       :key="`spread${i}`"
                       :eager="eagerLoad(i)"
                       class="full-height"
                       :class="preRender(i) ? 'pre-render' : ''"
                       :transition="animations ? undefined : false"
                       :reverse-transition="animations ? undefined : false"
      >
        <div class="full-height d-flex flex-column justify-center">
          <div :class="`d-flex flex-row${flipDirection ? '-reverse' : ''} justify-center px-0 mx-0`">
            <img v-for="(page, j) in spread"
                 :alt="`Page ${page.number}`"
                 :key="`spread${i}-${j}`"
                 :src="page.url"
                 :class="imgClass(spread)"
                 class="img-fit-all"
            />
          </div>
        </div>
      </v-carousel-item>
    </v-carousel>

    <!--  clickable zone: left  -->
    <div v-if="!vertical"
         @click="onClickLeft()"
         class="left-quarter"
         style="z-index: 1;"
    />

    <!--  clickable zone: right  -->
    <div v-if="!vertical"
         @click="onClickRight()"
         class="right-quarter"
         style="z-index: 1;"
    />

    <!--  clickable zone: top  -->
    <div v-if="vertical"
         @click="onClickTop()"
         class="top-quarter"
         style="z-index: 1;"
    />

    <!--  clickable zone: bottom  -->
    <div v-if="vertical"
         @click="onClickBottom()"
         class="bottom-quarter"
         style="z-index: 1;"
    />

    <!--  clickable zone: menu  -->
    <div @click="onClickCenter()"
         :class="`${vertical ? 'center-vertical' : 'center-horizontal'}`"
         style="z-index: 1;"
    />
  </div>
</template>

<script lang="ts">
import Vue from 'vue'
import {ReadingDirection} from '@/types/enum-books'
import {PagedReaderLayout, ScaleType} from '@/types/enum-reader'
import {shortcutsLTR, shortcutsRTL, shortcutsVertical} from '@/functions/shortcuts/paged-reader'
import {PageDtoWithUrl} from '@/types/komga-books'
import {buildSpreads} from '@/functions/book-spreads'
import {throttle} from 'lodash'

export default Vue.extend({
  name: 'PagedReader',
  data: function () {
    return {
      logger: 'PagedReader',
      carouselPage: 0,
      spreads: [] as PageDtoWithUrl[][],
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
    page: {
      type: Number,
      required: true,
    },
    pageLayout: {
      type: String as () => PagedReaderLayout,
      required: true,
    },
    animations: {
      type: Boolean,
      required: true,
    },
    swipe: {
      type: Boolean,
      required: true,
    },
    readingDirection: {
      type: String as () => ReadingDirection,
      required: true,
    },
    scale: {
      type: String as () => ScaleType,
      required: true,
    },
  },
  watch: {
    pages: {
      handler(val) {
        this.spreads = buildSpreads(val, this.pageLayout)
      },
      immediate: true,
    },
    carouselPage(val, old) {
      this.$debug('[watch:carouselPage', `old:${old}`, `new:${val}`)
      if (this.carouselPage >= 0 && this.carouselPage < this.spreads.length && this.spreads.length > 0) {
        const currentSpread = this.spreads[this.carouselPage]
        const currentPage = currentSpread.length == 2 && currentSpread[1].mediaType ? currentSpread[1] : currentSpread[0]
        this.$emit('update:page', currentPage.number)
      } else {
        this.$emit('update:page', 1)
      }
    },
    page(val, old) {
      this.$debug('[watch:page]', `old:${old}`, `new:${val}`)
      const spreadIndex = this.toSpreadIndex(val)
      this.$debug('[watch:page]', `toSpreadIndex:${spreadIndex}`)
      this.carouselPage = spreadIndex
    },
    pageLayout: {
      handler(val) {
        const current = this.page
        this.spreads = buildSpreads(this.pages, val)
        this.carouselPage = this.toSpreadIndex(current)
      },
      immediate: true,
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
  computed: {
    shortcuts(): any {
      const shortcuts = []
      switch (this.readingDirection) {
        case ReadingDirection.LEFT_TO_RIGHT:
          shortcuts.push(...shortcutsLTR)
          break
        case ReadingDirection.RIGHT_TO_LEFT:
          shortcuts.push(...shortcutsRTL)
          break
        case ReadingDirection.VERTICAL:
          shortcuts.push(...shortcutsVertical)
          break
      }
      return this.$_.keyBy(shortcuts, x => x.key)
    },
    flipDirection(): boolean {
      return this.readingDirection === ReadingDirection.RIGHT_TO_LEFT
    },
    vertical(): boolean {
      return this.readingDirection === ReadingDirection.VERTICAL
    },
    currentSlide(): number {
      return this.carouselPage + 1
    },
    slidesCount(): number {
      return this.spreads.length
    },
    canPrev(): boolean {
      return this.currentSlide > 1
    },
    canNext(): boolean {
      return this.currentSlide < this.slidesCount
    },
    isDoublePages(): boolean {
      return this.pageLayout === PagedReaderLayout.DOUBLE_PAGES || this.pageLayout === PagedReaderLayout.DOUBLE_NO_COVER
    },
  },
  methods: {
    keyPressed: throttle(function (this: any, e: KeyboardEvent) {
      this.shortcuts[e.key]?.execute(this)
    }, 500),
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
        const maxScrollY = document.documentElement.scrollHeight - window.innerHeight
        const maxScrollX = document.documentElement.scrollWidth - window.innerWidth
        if (maxScrollY > 0 || maxScrollX > 0) {
          window.scrollTo(this.dragStartScrollLeft - deltaX, this.dragStartScrollTop - deltaY)
        }
        e.preventDefault()
      }
    },
    onMouseUp(e: MouseEvent) {
      if (!this.isMouseDown) return
      this.isMouseDown = false
      window.removeEventListener('mousemove', this.onMouseMove)
      window.removeEventListener('mouseup', this.onMouseUp)
      if (this.hasDragged) {
        const deltaX = e.clientX - this.dragStartX
        const deltaY = e.clientY - this.dragStartY
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

        if (this.swipe) {
          const swipeThreshold = 50
          if (this.vertical) {
            if (Math.abs(deltaY) > swipeThreshold && Math.abs(deltaY) > Math.abs(deltaX)) {
              if (deltaY < 0) this.verticalNext()
              else this.verticalPrev()
            }
          } else {
            if (Math.abs(deltaX) > swipeThreshold && Math.abs(deltaX) > Math.abs(deltaY)) {
              if (deltaX < 0) this.turnRight()
              else this.turnLeft()
            }
          }
        }
      }
    },
    onClickLeft() {
      if (this.hasDragged) return
      this.turnLeft()
    },
    onClickRight() {
      if (this.hasDragged) return
      this.turnRight()
    },
    onClickTop() {
      if (this.hasDragged) return
      this.verticalPrev()
    },
    onClickBottom() {
      if (this.hasDragged) return
      this.verticalNext()
    },
    onClickCenter() {
      if (this.hasDragged) return
      this.centerClick()
    },
    imgClass(spread: PageDtoWithUrl[]): string {
      const double = spread.length > 1
      switch (this.scale) {
        case ScaleType.WIDTH:
          return double ? 'img-double-fit-width' : 'img-fit-width'
        case ScaleType.WIDTH_SHRINK_ONLY:
          return double ? 'img-double-fit-width-shrink-only' : 'img-fit-width-shrink-only'
        case ScaleType.HEIGHT:
          return 'img-fit-height'
        case ScaleType.SCREEN:
          return double ? 'img-double-fit-screen' : 'img-fit-screen'
        default:
          return 'img-fit-original'
      }
    },
    eagerLoad(spreadIndex: number): boolean {
      return Math.abs(this.carouselPage - spreadIndex) <= 2
    },
    preRender(spreadIndex: number): boolean {
      return Math.abs(this.carouselPage - spreadIndex) > (this.animations ? 1 : 0)
    },
    centerClick() {
      this.$emit('menu')
    },
    turnRight() {
      if (!this.vertical)
        this.flipDirection ? this.prev() : this.next()
    },
    turnLeft() {
      if (!this.vertical)
        this.flipDirection ? this.next() : this.prev()
    },
    verticalPrev() {
      if (this.vertical) this.prev()
    },
    verticalNext() {
      if (this.vertical) this.next()
    },
    prev() {
      if (this.canPrev) {
        this.carouselPage--
        window.scrollTo(0, 0)
      } else {
        this.$emit('jump-previous')
      }
    },
    next() {
      if (this.canNext) {
        this.carouselPage++
        window.scrollTo(0, 0)
      } else {
        this.$emit('jump-next')
      }
    },
    toSpreadIndex(i: number): number {
      this.$debug('[toSpreadIndex]', `i:${i}`, `isDoublePages:${this.isDoublePages}`)
      if (this.spreads.length > 0) {
        if (this.isDoublePages) {
          for (let j = 0; j < this.spreads.length; j++) {
            for (let k = 0; k < this.spreads[j].length; k++) {
              if (this.spreads[j][k].number === i) {
                return j
              }
            }
          }
        } else {
          return i - 1
        }
      }
      return i - 1
    },
  },
})
</script>
<style scoped>
.paged-reader {
  user-select: none;
  -webkit-user-select: none;
}

.paged-reader img {
  user-select: none;
  -webkit-user-select: none;
  -webkit-user-drag: none;
}

.full-height {
  height: 100%;
}

.left-quarter {
  top: 0;
  left: 0;
  width: 25%;
  height: 100%;
  position: absolute;
}

.right-quarter {
  top: 0;
  right: 0;
  width: 25%;
  height: 100%;
  position: absolute;
}

.top-quarter {
  top: 0;
  height: 25%;
  width: 100%;
  position: absolute;
}

.bottom-quarter {
  bottom: 0;
  height: 25%;
  width: 100%;
  position: absolute;
}

.center-horizontal {
  top: 0;
  left: 25%;
  width: 50%;
  height: 100%;
  position: absolute;
}

.center-vertical {
  top: 25%;
  height: 50%;
  width: 100%;
  position: absolute;
}

.img-fit-all {
  object-fit: contain;
  object-position: center;
}

.img-fit-width {
  width: 100vw;
  min-height: 100vh;
  align-self: flex-start;
}

.img-double-fit-width {
  width: 50vw;
  min-height: 100vh;
  align-self: flex-start;
}

.img-fit-width-shrink-only {
  max-width: 100vw;
  align-self: flex-start;
}

.img-double-fit-width-shrink-only {
  max-width: 50vw;
  align-self: flex-start;
}

.img-fit-original {
  width: auto;
  height: auto;
}

.img-fit-height {
  min-height: 100vh;
  height: 100vh;
}

.img-fit-screen {
  width: 100vw;
  height: 100vh;
}

.img-double-fit-screen {
  max-width: 50vw;
  height: 100vh;
}

.pre-render {
  display: block !important;
  position: fixed;
  right: -1000vw;
  top: -1000vh;
}
</style>
