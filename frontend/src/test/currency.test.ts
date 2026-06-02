import { describe, it, expect } from 'vitest'
import { formatCurrency, paiseToRupees, rupeesToPaise } from '../lib/currency'

describe('currency utils', () => {
  it('formats paise to INR string', () => {
    expect(formatCurrency(150000)).toMatch('1,500')
    expect(formatCurrency(0)).toMatch('0')
  })
  it('converts paise to rupees correctly', () => {
    expect(paiseToRupees(100)).toBe(1)
    expect(paiseToRupees(150025)).toBe(1500.25)
  })
  it('converts rupees to paise with rounding', () => {
    expect(rupeesToPaise(1)).toBe(100)
    expect(rupeesToPaise(1500.25)).toBe(150025)
  })
})
