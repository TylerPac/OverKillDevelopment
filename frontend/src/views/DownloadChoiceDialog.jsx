import { useEffect, useRef, useState } from 'react';
import { getPartDownloadLink } from '../services/downloadsService';

const ERRORS = {
  download_not_ready: 'That download is being rebuilt with a new version. Try again in a minute.',
  download_unavailable: 'Downloads are unavailable right now. Try again shortly.',
  purchase_required: 'You need to buy this product first.',
  account_not_setup: 'Link your Steam account first.',
};

// Lets a buyer choose the client or server files. Server files are only for the game server, so choosing them
// asks for confirmation that they will not be uploaded to the Steam Workshop.
export default function DownloadChoiceDialog({ productName, productId, options, onClose }) {
  const dialogRef = useRef(null);
  const [confirmServer, setConfirmServer] = useState(false);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');

  // Opened as a modal; it is removed from the page when the parent stops rendering it, so no cleanup
  // (closing it here would fire onClose, and React runs effects twice in development)
  useEffect(() => {
    const dialog = dialogRef.current;
    if (dialog && !dialog.open) dialog.showModal();
  }, []);

  async function download(part) {
    setBusy(true);
    setError('');
    try {
      const url = await getPartDownloadLink(productId, part);
      window.location.href = url;
      onClose();
    } catch (err) {
      setError(ERRORS[err.message] || `Download failed (${err.message}).`);
    } finally {
      setBusy(false);
    }
  }

  return (
    <dialog ref={dialogRef} className="dialog" onClose={onClose} aria-labelledby="download-choice-title">
      {!confirmServer ? (
        <>
          <p className="kicker"><span className="tag">Download</span></p>
          <h2 id="download-choice-title">{productName}</h2>
          <p className="muted">Choose which files you need.</p>
          <div className="dialog-choices">
            {options.client && (
              <button type="button" className="btn btn-primary" disabled={busy} onClick={() => download('client')}>
                Client files
                <span className="choice-note">Mod for players. This is what goes on the Steam Workshop.</span>
              </button>
            )}
            {options.server && (
              <button type="button" className="btn" disabled={busy} onClick={() => setConfirmServer(true)}>
                Server files
                <span className="choice-note">For your game server only.</span>
              </button>
            )}
          </div>
        </>
      ) : (
        <>
          <p className="kicker"><span className="tag">Warning</span></p>
          <h2 id="download-choice-title">Server files only</h2>
          <p className="notice warn" role="alert">
            These files are for your server only. <strong>Do NOT upload them to the Steam Workshop</strong> or share them
            with players. Players only need the client files.
          </p>
          <div className="button-row">
            <button type="button" className="btn btn-primary" disabled={busy} onClick={() => download('server')}>
              I understand, download server files
            </button>
            <button type="button" className="btn" onClick={() => setConfirmServer(false)}>Back</button>
          </div>
        </>
      )}
      {error && <p className="notice error">{error}</p>}
      <button type="button" className="btn-text dialog-close" onClick={onClose} aria-label="Close">✕</button>
    </dialog>
  );
}
