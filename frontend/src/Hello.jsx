import { useState, useEffect } from 'react'
import './Hello.css'

function Hello() {
  const [message, setMessage] = useState('Loading...')

  useEffect(() => {
    fetch('http://localhost:8081/hello')
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
