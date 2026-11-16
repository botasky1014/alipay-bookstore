<script setup>
import { money } from '../utils/format'

defineProps({
  books: { type: Array, required: true },
  linkPrefix: { type: String, required: true },
})
</script>

<template>
  <ul class="book-grid">
    <li v-for="book in books" :key="book.id">
      <RouterLink :to="`${linkPrefix}${book.id}`" class="book-card">
        <img :src="book.coverImage" :alt="book.title" class="book-card__cover" loading="lazy" />
        <div class="book-card__body">
          <div class="book-card__meta">
            <span class="price">{{ money(book.price) }}</span>
            <span :class="['stock', { 'stock--out': book.stock === 0 }]">
              {{ book.stock === 0 ? 'Out of stock' : `Stock: ${book.stock}` }}
            </span>
          </div>
          <div class="book-card__title">{{ book.title }}</div>
        </div>
      </RouterLink>
    </li>
  </ul>
</template>
