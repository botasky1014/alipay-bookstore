<script setup>
import { onMounted, ref } from 'vue'

import { bookApi } from '../api'
import BookGrid from '../components/BookGrid.vue'

const books = ref([])
const error = ref('')

onMounted(async () => {
  try {
    books.value = await bookApi.list()
  } catch (e) {
    error.value = e.message
  }
})
</script>

<template>
  <h1 class="page-title">Books</h1>
  <p v-if="error" class="form-error">{{ error }}</p>
  <BookGrid :books="books" link-prefix="/books/" />
</template>
