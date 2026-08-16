import type { Meta, StoryObj } from '@storybook/vue3-vite'

import Collection from './Collection.vue'
import { fn } from 'storybook/test'

import { mockPage } from '@/mocks/api/pageable'
import { PageRequest } from '@/types/PageRequest'
import { handleGetCollections } from '@/generated/openapi/msw.gen'

import { response200OK } from '@/mocks/api/utils'

const meta = {
  component: Collection,
  render: (args: object) => ({
    components: { Collection },
    setup() {
      return { args }
    },
    template: '<Collection v-model="args.modelValue"/>',
  }),
  parameters: {
    docs: {
      description: {
        component: 'Collection filter.',
      },
    },
  },
  args: {
    'onUpdate:modelValue': fn(),
    modelValue: [],
  },
} satisfies Meta<typeof Collection>

export default meta
type Story = StoryObj<typeof meta>

export const Default: Story = {
  args: {},
}

export const NoData: Story = {
  beforeEach({ msw }) {
    msw.use(handleGetCollections(() => response200OK(mockPage([], new PageRequest()))))
  },
}

export const InitialValue: Story = {
  args: {
    modelValue: [{ i: 'i', v: '026801S4HWRZA' }],
  },
}

export const InitialValueOutsideShown: Story = {
  args: {
    modelValue: [{ i: 'i', v: '026801S4HWRZB' }],
  },
}
