import React from 'react'
import ReactDOM from 'react-dom/client'
import { BrowserRouter } from 'react-router-dom'
import { QueryClientProvider } from '@tanstack/react-query'
import { ReactQueryDevtools } from '@tanstack/react-query-devtools'
import App from './App'
import { queryClient } from './lib/queryClient'
import './index.css'

// Prevent mouse-wheel from changing values in number inputs globally.
// When a number input is focused and the user scrolls the page, browsers
// increment/decrement the value by default — this blurs the input first so
// the scroll event is handled by the page instead.
document.addEventListener(
  'wheel',
  () => {
    const el = document.activeElement as HTMLInputElement | null
    if (el?.type === 'number') {
      el.blur()
    }
  },
  { passive: true },
)

ReactDOM.createRoot(document.getElementById('root')!).render(
  <React.StrictMode>
    <QueryClientProvider client={queryClient}>
      <BrowserRouter>
        <App />
      </BrowserRouter>
      <ReactQueryDevtools initialIsOpen={false} />
    </QueryClientProvider>
  </React.StrictMode>
)
