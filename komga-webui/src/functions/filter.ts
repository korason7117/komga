import {
  SearchConditionAgeRating,
  SearchConditionAllOfBook,
  SearchConditionAllOfSeries,
  SearchConditionAnyOfBook,
  SearchConditionAnyOfSeries,
  SearchConditionBook,
  SearchConditionLanguage,
  SearchConditionPublisher,
  SearchConditionSeries,
} from '@/types/komga-search'
import {FiltersActive, NameValue} from '@/types/filter'

export function sortOrFilterActive(sortActive: SortActive, sortDefault: SortActive, filters: FiltersActive): boolean {
  const sortCustom = sortActive.key !== sortDefault.key || sortActive.order !== sortDefault.order
  const filterCustom = Object.keys(filters).some(x => filters[x].length !== 0)
  return sortCustom || filterCustom
}

export function mergeFilterParams (filter: FiltersActive, query: any) {
  for (const f of Object.keys(filter)) {
    if (filter[f].length !== 0) query[f] = filter[f]
  }
}

export function toNameValue(list: string[]): NameValue[] {
  return list.map(x => ({name: x, value: x} as NameValue))
}

export function toNameValueCondition(list: string[], valueSupplier: (x: any) => SearchConditionSeries, nValueSupplier?: (x: any) => SearchConditionSeries): NameValue[] {
  return list.map(x => ({name: x, value: valueSupplier(x), nValue: nValueSupplier ? nValueSupplier(x) : undefined} as NameValue))
}

/**
 * Determines whether a single filter-item condition (i.e. one entry of
 * this.filters[key], corresponding to one clicked chip) represents an
 * exclude ("NOT X") rather than an include ("X").
 *
 * Most conditions follow the straightforward convention used throughout
 * BrowseLibraries.vue / BrowseBooks.vue / BrowseSeries.vue: operator
 * 'isNot' (or 'isNull', for the "(Any)" pseudo-chip on nullable fields
 * like genre/folder/tag/releaseDate/sharingLabel) means exclude,
 * everything else means include. Three fields deviate from that
 * convention and need special-casing:
 *
 *  - SearchConditionAgeRating's "Unknown" chip reverses the null
 *    convention: include is 'isNull', exclude is 'isNotNull'.
 *  - SearchConditionPublisher / SearchConditionLanguage's "(Any)" chip
 *    is encoded as is/isNot on an empty-string sentinel, with the
 *    meaning reversed from every other is/isNot chip: include is
 *    'isNot' (has a non-empty value), exclude is 'is' (value is empty).
 *  - The releaseDate year-picker condition is itself a composite:
 *    include is wrapped in SearchConditionAllOfSeries, exclude in
 *    SearchConditionAnyOfSeries (see filterOptions.releaseDate).
 */
export function isExcludeCondition(condition: any): boolean {
  if (condition instanceof SearchConditionAnyOfSeries || condition instanceof SearchConditionAnyOfBook) return true
  if (condition instanceof SearchConditionAllOfSeries || condition instanceof SearchConditionAllOfBook) return false

  const operatorHolder = condition ? Object.values(condition)[0] as any : undefined
  const operator = operatorHolder?.operator

  if (condition instanceof SearchConditionAgeRating) return operator === 'isNot' || operator === 'isNotNull'

  if ((condition instanceof SearchConditionPublisher || condition instanceof SearchConditionLanguage) && operatorHolder?.value === '')
    return operator === 'is'

  return operator === 'isNot' || operator === 'isNull'
}

/**
 * Combines a flat list of per-item filter conditions - which may contain
 * a mix of include and exclude conditions, e.g. "Folder is MangaDex" and
 * "Folder is not Asura Scans" - into a single condition, without letting
 * excludes leak into the include OR/AND grouping (and vice versa).
 *
 * Includes are combined with AND (allOf=true) or OR (allOf=false), per
 * the existing "All of / Any of" toggle. Excludes are always AND-ed
 * together: since each exclude condition already asserts "NOT X", ANDing
 * them implements "NOT X1 AND NOT X2" == "NOT (X1 OR X2)" - i.e. exclude
 * anything matching any of the excluded values. The include group and
 * the exclude group are then AND-ed together.
 */
function combineFilterCondition<T>(
  items: T[] | undefined,
  allOf: boolean,
  AllOfCtor: new (c: T[]) => T,
  AnyOfCtor: new (c: T[]) => T,
): T | undefined {
  if (!items || items.length === 0) return undefined

  const includes = items.filter(c => !isExcludeCondition(c))
  const excludes = items.filter(c => isExcludeCondition(c))

  const parts: T[] = []
  if (includes.length > 0) parts.push(allOf ? new AllOfCtor(includes) : new AnyOfCtor(includes))
  if (excludes.length > 0) parts.push(new AllOfCtor(excludes))

  if (parts.length === 0) return undefined
  if (parts.length === 1) return parts[0]
  return new AllOfCtor(parts)
}

export function buildFilterCondition(items: SearchConditionSeries[] | undefined, allOf: boolean): SearchConditionSeries | undefined {
  return combineFilterCondition(items, allOf, SearchConditionAllOfSeries, SearchConditionAnyOfSeries)
}

export function buildFilterConditionBook(items: SearchConditionBook[] | undefined, allOf: boolean): SearchConditionBook | undefined {
  return combineFilterCondition(items, allOf, SearchConditionAllOfBook, SearchConditionAnyOfBook)
}

export function extractFilterOptionsValues(options: NameValue[] | undefined): any[] {
  const r: any[] = []
  options?.forEach(x => {
      r.push(x.value)
      if (x.nValue) r.push(x.nValue)
    })
  return r
}
