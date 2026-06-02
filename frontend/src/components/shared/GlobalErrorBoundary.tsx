import { Component, type ReactNode } from 'react'
interface Props { children: ReactNode }
interface State { hasError: boolean; message: string }
export class GlobalErrorBoundary extends Component<Props, State> {
  state: State = { hasError: false, message: '' }
  static getDerivedStateFromError(error: Error): State { return { hasError: true, message: error.message } }
  render() {
    if (this.state.hasError) return (
      <div className="min-h-screen flex items-center justify-center bg-gray-50">
        <div className="text-center p-8">
          <h1 className="text-2xl font-bold text-red-600 mb-2">Something went wrong</h1>
          <p className="text-gray-600 mb-4">{this.state.message}</p>
          <button onClick={() => window.location.reload()} className="px-4 py-2 bg-blue-600 text-white rounded-lg hover:bg-blue-700">Reload</button>
        </div>
      </div>
    )
    return this.props.children
  }
}
