<script setup>
import { useToastStore } from '../stores/toast'

const toast = useToastStore()
</script>

<template>
  <div class="toasts" aria-live="polite">
    <div v-for="item in toast.toasts" :key="item.id" :class="['toast', `toast--${item.type}`]"
      @click="toast.dismiss(item.id)">
      {{ item.message }}
    </div>
  </div>
</template>
