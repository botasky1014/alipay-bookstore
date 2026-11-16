<script setup>
import { statusLabel } from '../utils/format'

defineProps({ status: { type: String, required: true } })
</script>

<template>
  <span :class="['status', `status--${status.toLowerCase().replace('_', '-')}`]">{{ statusLabel(status) }}</span>
</template>
