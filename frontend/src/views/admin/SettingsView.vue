<script setup>
import { computed, onMounted, ref } from 'vue'

import { adminApi } from '../../api'
import { useToastStore } from '../../stores/toast'
import { dateTime } from '../../utils/format'

const toast = useToastStore()
const notifyUrl = ref('')
const returnUrl = ref('')
const saved = ref(null)
const error = ref('')
const busy = ref(false)

const incomplete = computed(() => saved.value && (!saved.value.notifyUrl || !saved.value.returnUrl))

function apply(setting) {
  saved.value = setting
  notifyUrl.value = setting.notifyUrl
  returnUrl.value = setting.returnUrl
}

onMounted(async () => {
  try {
    apply(await adminApi.getAlipayUrls())
  } catch (e) {
    error.value = e.message
  }
})

async function save() {
  error.value = ''
  busy.value = true
  try {
    apply(await adminApi.updateAlipayUrls(notifyUrl.value, returnUrl.value))
    toast.success('Alipay URLs saved. They apply to the next payment.')
  } catch (e) {
    error.value = e.message
  } finally {
    busy.value = false
  }
}
</script>

<template>
  <h1 class="page-title">Settings</h1>
  <form class="card settings" @submit.prevent="save">
    <h2>Alipay URLs</h2>
    <p class="muted">
      Both URLs go through your tunnel. Point the tunnel at port <strong>3000</strong>; nginx there serves the
      return page and forwards <code>/api</code> to the backend, so one tunnel address covers both URLs.
      Changes take effect on the next payment without restarting anything.
    </p>

    <label class="field">
      <span>Notify URL</span>
      <input v-model="notifyUrl" type="url" placeholder="https://abc.example-tunnel.dev/api/alipay/notify" />
      <small class="muted">
        Alipay's server calls this when a payment completes. Path: <code>/api/alipay/notify</code>.
        <template v-if="saved?.notifyUrl"> Last changed {{ dateTime(saved.notifyUrlUpdatedAt) }}.</template>
      </small>
    </label>

    <label class="field">
      <span>Return URL</span>
      <input v-model="returnUrl" type="url" placeholder="https://abc.example-tunnel.dev/pay/return" />
      <small class="muted">
        Alipay sends the buyer's browser here after paying. Path: <code>/pay/return</code>.
        <template v-if="saved?.returnUrl"> Last changed {{ dateTime(saved.returnUrlUpdatedAt) }}.</template>
      </small>
    </label>

    <p v-if="incomplete" class="form-error small">Not fully configured yet. Buyers cannot pay until both URLs are set.</p>
    <p v-if="error" class="form-error" role="alert">{{ error }}</p>
    <button type="submit" class="button button--primary" :disabled="busy">Save</button>
  </form>
</template>
