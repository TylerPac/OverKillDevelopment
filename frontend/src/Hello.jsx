import { useState, useEffect } from 'react'
import './Hello.css'

const apiBase =
  (typeof import.meta !== 'undefined' && import.meta.env && import.meta.env.VITE_API_BASE_URL) ||
  'http://localhost:8080'

function Hello() {
  const [message, setMessage] = useState('Loading...')

  useEffect(() => {
    fetch(`${apiBase}/hello`)
      .then((res) => res.text())
      .then((text) => setMessage(text))
      .catch(() => setMessage('Failed to load'))
  }, [])

  return (
    <div className="HelloText">
      <h1>{message}</h1>
    </div>
  )
}

export default Hello
