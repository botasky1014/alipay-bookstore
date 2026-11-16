<script setup>
defineProps({ title: { type: String, required: true } })
const emit = defineEmits(['close'])
</script>

<template>
  <div class="overlay" @click.self="emit('close')">
    <div class="modal" role="dialog" aria-modal="true" :aria-label="title">
      <div class="modal__header">
        <h2>{{ title }}</h2>
        <button type="button" class="icon-button" aria-label="Close" @click="emit('close')">&times;</button>
      </div>
      <slot />
    </div>
  </div>
</template>
