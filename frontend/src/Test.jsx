import { useState, useEffect } from 'react'
import './Hello.css'

const apiBase =
  (typeof import.meta !== 'undefined' && import.meta.env && import.meta.env.VITE_API_BASE_URL) ||
  'http://localhost:8080'

function Test() {
  const [textVariable, settextVariable] = useState('Loading...')

  useEffect(() => {
    fetch(`${apiBase}/test`)
      // This is where the error is occurring. 
      // The response from the backend is not being properly handled, which is causing the frontend to fail to load the message.
      .then((res) => res.text())
      .then((text) => settextVariable(text))
      // if the fetch is successful, set textVariable to the response text
      .catch(() => settextVariable('Failed to load'))
      // if failed to load, set textVariable to "Failed to load"
  }, [])

  return (
    <div className="TestText">
      <h1>{textVariable}</h1>
    </div>
  )
}

export default Test
