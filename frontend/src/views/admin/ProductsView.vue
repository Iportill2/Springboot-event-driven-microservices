<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import {
  createProduct,
  deleteProduct,
  errorMessage,
  fetchProducts,
  updateProduct,
} from '../../api/client'
import type { Product, ProductPayload } from '../../api/types'
import { formatDate, formatPrice } from '../../format'

interface FormState {
  name: string
  sku: string
  description: string
  price: string
}

const products = ref<Product[]>([])
const loading = ref(true)
const saving = ref(false)
const notice = ref<{ text: string; kind: 'ok' | 'error' } | null>(null)
const ready = ref(false)

const showForm = ref(false)
const editingId = ref<number | null>(null)
const form = reactive<FormState>({ name: '', sku: '', description: '', price: '' })

onMounted(async () => {
  requestAnimationFrame(() => {
    requestAnimationFrame(() => (ready.value = true))
  })
  await load()
})

async function load() {
  try {
    products.value = await fetchProducts()
  } catch (e: unknown) {
    notice.value = { text: errorMessage(e, 'No se pudo cargar el catálogo'), kind: 'error' }
  } finally {
    loading.value = false
  }
}

// Refleja las validaciones de ProductRequest: @NotBlank @Size(max=100/50/500)
// y @NotNull @DecimalMin("0.01"). El backend sigue siendo la autoridad, esto
// solo evita un viaje de ida y vuelta por los errores obvios.
function validate(): string | null {
  const name = form.name.trim()
  const sku = form.sku.trim()
  if (!name) return 'El nombre es obligatorio'
  if (name.length > 100) return 'El nombre no puede pasar de 100 caracteres'
  if (!sku) return 'El SKU es obligatorio'
  if (sku.length > 50) return 'El SKU no puede pasar de 50 caracteres'
  if (form.description.length > 500) return 'La descripción no puede pasar de 500 caracteres'
  const price = Number(form.price)
  if (!form.price.trim() || Number.isNaN(price)) return 'El precio es obligatorio'
  if (price < 0.01) return 'El precio mínimo es 0,01'
  return null
}

function openCreate() {
  editingId.value = null
  form.name = ''
  form.sku = ''
  form.description = ''
  form.price = ''
  notice.value = null
  showForm.value = true
}

function openEdit(product: Product) {
  editingId.value = product.id
  form.name = product.name
  form.sku = product.sku
  form.description = product.description ?? ''
  form.price = product.price.toFixed(2)
  notice.value = null
  showForm.value = true
}

function closeForm() {
  showForm.value = false
  editingId.value = null
}

async function save() {
  const invalid = validate()
  if (invalid) {
    notice.value = { text: invalid, kind: 'error' }
    return
  }

  const payload: ProductPayload = {
    name: form.name.trim(),
    sku: form.sku.trim(),
    description: form.description.trim() || undefined,
    price: Number(form.price),
  }

  saving.value = true
  const creating = editingId.value === null
  try {
    if (creating) {
      await createProduct(payload)
    } else {
      await updateProduct(editingId.value as number, payload)
    }
    notice.value = { text: `Producto «${payload.name}» ${creating ? 'creado' : 'actualizado'}.`, kind: 'ok' }
    closeForm()
  } catch (e: unknown) {
    notice.value = { text: errorMessage(e, 'No se pudo guardar el producto'), kind: 'error' }
  } finally {
    saving.value = false
  }
  // Se recarga tambien tras un fallo: un 400 por SKU duplicado o un 404 por
  // producto borrado por otra sesion dejan la tabla desfasada.
  await load()
}

async function remove(product: Product) {
  if (!window.confirm(`¿Eliminar «${product.name}»? Esta acción no se puede deshacer.`)) return
  try {
    await deleteProduct(product.id)
    notice.value = { text: `Producto «${product.name}» eliminado.`, kind: 'ok' }
  } catch (e: unknown) {
    notice.value = { text: errorMessage(e, 'No se pudo eliminar el producto'), kind: 'error' }
  }
  await load()
}
</script>

<template>
  <div :class="['page', { 'is-ready': ready }]">
    <div class="page-head">
      <h1 class="reveal" style="--d: 40ms">Productos</h1>
      <p class="reveal" style="--d: 100ms">
        Alta, edición y borrado del catálogo. Requiere rol
        <code>ADMIN</code>.
      </p>
    </div>

    <p v-if="notice" :class="['notice', notice.kind === 'error' ? 'notice-error' : 'notice-ok']">
      {{ notice.text }}
    </p>

    <section v-if="showForm" class="panel glass">
      <h2 class="form-title">{{ editingId === null ? 'Nuevo producto' : 'Editar producto' }}</h2>
      <form class="form-grid" @submit.prevent="save">
        <label>
          Nombre
          <input v-model="form.name" class="field" type="text" maxlength="100" :disabled="saving" />
        </label>
        <label>
          SKU
          <input v-model="form.sku" class="field" type="text" maxlength="50" :disabled="saving" />
        </label>
        <label>
          Precio (€)
          <input
            v-model="form.price"
            class="field"
            type="number"
            step="0.01"
            min="0.01"
            :disabled="saving"
          />
        </label>
        <label class="wide">
          Descripción
          <textarea v-model="form.description" class="field" rows="3" maxlength="500" :disabled="saving"></textarea>
        </label>
        <div class="form-actions wide">
          <button type="submit" class="btn btn-primary" :disabled="saving">
            {{ saving ? 'Guardando…' : 'Guardar' }}
          </button>
          <button type="button" class="btn btn-ghost" :disabled="saving" @click="closeForm">Cancelar</button>
        </div>
      </form>
    </section>

    <section class="panel glass reveal" style="--d: 160ms">
      <div class="head-row">
        <h2>Catálogo</h2>
        <button type="button" class="btn btn-primary btn-sm" @click="openCreate">Nuevo producto</button>
      </div>

      <p v-if="loading" class="muted">Cargando…</p>
      <p v-else-if="!products.length" class="muted">No hay productos.</p>
      <div v-else class="table-wrap">
        <table class="data-table">
          <thead>
            <tr>
              <th>Nombre</th>
              <th>SKU</th>
              <th class="num">Precio</th>
              <th>Actualizado</th>
              <th></th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="p in products" :key="p.id">
              <td>
                <strong>{{ p.name }}</strong>
                <p v-if="p.description" class="desc">{{ p.description }}</p>
              </td>
              <td><code>{{ p.sku }}</code></td>
              <td class="num">{{ formatPrice(p.price) }}</td>
              <td class="muted">{{ formatDate(p.updatedAt) }}</td>
              <td>
                <div class="actions">
                  <button type="button" class="btn btn-ghost btn-sm" @click="openEdit(p)">Editar</button>
                  <button type="button" class="btn btn-ghost btn-sm danger" @click="remove(p)">Borrar</button>
                </div>
              </td>
            </tr>
          </tbody>
        </table>
      </div>
    </section>
  </div>
</template>

<style scoped>
.page {
  position: relative;
  z-index: 1;
}

.page-head code {
  font-size: 12px;
  color: var(--accent-2);
  background: var(--field-bg);
  border: 1px solid var(--glass-border);
  padding: 2px 6px;
  border-radius: 6px;
}

.form-title {
  margin: 0 0 16px;
  font-size: 17px;
  color: var(--text-h);
}

.head-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 16px;
}

.head-row h2 {
  margin: 0;
  font-size: 17px;
  color: var(--text-h);
}

.desc {
  margin: 4px 0 0;
  font-size: 13px;
  color: var(--muted);
  max-width: 42ch;
}

.data-table code {
  font-size: 12px;
  color: var(--accent-2);
  background: var(--field-bg);
  border: 1px solid var(--glass-border);
  padding: 3px 7px;
  border-radius: 7px;
  white-space: nowrap;
}

.btn.danger:hover {
  border-color: var(--danger);
  color: var(--danger);
}

.muted {
  color: var(--muted);
  margin: 0;
}
</style>
