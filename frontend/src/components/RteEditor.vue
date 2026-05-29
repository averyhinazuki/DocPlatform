<template>
  <div class="rte-wrap">
    <div :id="toolbarId">
      <span class="ql-formats">
        <button class="ql-bold"></button>
        <button class="ql-italic"></button>
        <button class="ql-underline"></button>
      </span>
      <span class="ql-formats">
        <button class="ql-header" value="1"></button>
        <button class="ql-header" value="2"></button>
      </span>
      <span class="ql-formats">
        <button class="ql-blockquote"></button>
        <button class="ql-hr-btn" @mousedown.prevent="insertHr" title="Horizontal rule">—</button>
      </span>
      <span class="ql-formats">
        <button class="ql-list" value="bullet"></button>
        <button class="ql-list" value="ordered"></button>
      </span>
    </div>
    <QuillEditor
      ref="editorRef"
      v-model:content="html"
      content-type="html"
      :toolbar="`#${toolbarId}`"
      theme="snow"
    />
  </div>
</template>

<script setup>
import { ref, watch } from 'vue'
import { QuillEditor } from '@vueup/vue-quill'
import Quill from 'quill'
import '@vueup/vue-quill/dist/vue-quill.snow.css'

const BlockEmbed = Quill.import('blots/block/embed')
class DividerBlot extends BlockEmbed {}
DividerBlot.blotName = 'divider'
DividerBlot.tagName = 'hr'
Quill.register(DividerBlot, /* overwrite= */ true)

const props = defineProps({
  modelValue: { type: String, default: '' }
})
const emit = defineEmits(['update:modelValue'])

const toolbarId = `rte-tb-${Math.random().toString(36).slice(2, 8)}`
const editorRef = ref(null)
const html = ref(props.modelValue)

watch(() => props.modelValue, val => { if (val !== html.value) html.value = val })
watch(html, val => emit('update:modelValue', val))

function insertHr() {
  const quill = editorRef.value.getQuill()
  const range = quill.getSelection(true)
  quill.insertEmbed(range.index, 'divider', true, 'user')
  quill.setSelection(range.index + 1, 0)
}
</script>

<style scoped>
.rte-wrap {
  border: 1px solid var(--border, #e2e8f0);
  border-radius: 6px;
  overflow: hidden;
}
.ql-hr-btn {
  font-size: 14px;
  line-height: 1;
  padding: 3px 6px;
}
</style>
