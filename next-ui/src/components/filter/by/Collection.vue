<template>
  <FilterSearchList
    v-model="model"
    v-model:mode="modelMode"
    v-model:search="search"
    :items="infiniteItems"
    :search-items="searchResults"
    :search-loading="searchLoading"
    :hide-search="hideSearch"
    show-mode-selector
    @load-more="loadNextPage()"
  >
  </FilterSearchList>
</template>

<script setup lang="ts">
import { useInfiniteQuery, useQuery } from '@pinia/colada'
import { collectionsListQuery, collectionsListQueryInfinite } from '@/colada/collections'
import { PageRequest } from '@/types/PageRequest'
import * as v from 'valibot'
import { type AnyAll, filterKeys, SchemaString } from '@/types/filter'
import type { ItemType } from '@/components/filter/List.vue'
import { refDebounced } from '@vueuse/core'
import type { CollectionDto } from '@/generated/openapi'

type SchString = v.InferOutput<typeof SchemaString>

const model = defineModel<SchString[]>({ default: () => [] })
const modelMode = defineModel<AnyAll>('mode', { default: 'anyOf' })

const search = ref()
const searchDebounced = refDebounced(search, 500)

const filterContext = inject(filterKeys.context, {})

const libraryIds = computed(() => toValue(filterContext)?.library_id)

const { data: searchItems, isLoading: searchLoading } = useQuery(() => ({
  ...collectionsListQuery({
    pageRequest: PageRequest.Unpaged(),
    search: searchDebounced.value,
    libraryIds: libraryIds.value,
  }),
  enabled: !!searchDebounced.value,
}))
const searchResults = computed(() => searchItems.value?.content?.map((it) => toItemType(it)))

const { data: infiniteData, loadNextPage } = useInfiniteQuery(() =>
  collectionsListQueryInfinite({ libraryIds: libraryIds.value }),
)
const infiniteItems = computed(
  () =>
    infiniteData.value?.pages.flatMap((it) => it?.content ?? []).map((it) => toItemType(it)) ?? [],
)

const hideSearch = computed(() => (infiniteData.value?.pages?.[0]?.totalElements || 0) < 10)

function toItemType(collection: CollectionDto): ItemType<SchString> {
  return {
    title: collection.name,
    value: { i: 'i', v: collection.id },
    valueExclude: { i: 'e', v: collection.id },
  }
}
</script>

<script lang="ts"></script>

<style scoped></style>
