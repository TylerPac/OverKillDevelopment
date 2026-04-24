import { useState, useEffect, useRef } from 'react';

// Showcase metadata per product — update text/features/changelog as mods evolve
const productMeta = {
  'keycard-crates': {
    images: [
      '/products/keycard-crates.jpg',
      '/products/keycard-crates-2.jpg',
      '/products/keycard-crates-3.jpg',
    ],
    tagline: 'Full-featured keycard and crate system for DayZ servers.',
    features: [
      'Fully customizable keycard tiers (Common → Legendary)',
      'Dynamic, per-crate loot tables',
      'Admin tools and RCON support',
      'Multiplayer-safe and performance-optimised',
      'Active development and dedicated support',
    ],
    changelog: [
      { version: '1.3.0', date: '2026-04-01', notes: 'Added new crate variants and improved admin panel UI.' },
      { version: '1.2.0', date: '2026-02-15', notes: 'Performance improvements, reduced server overhead, and several bug fixes.' },
      { version: '1.0.0', date: '2025-12-01', notes: 'Initial public release.' },
    ],
  },
  'weapon-system': {
    images: [
      '/products/weapon-system.jpg',
      '/products/weapon-system-2.jpg',
      '/products/weapon-system-3.jpg',
    ],
    tagline: 'Advanced weapon modding toolkit for DayZ.',
    features: [
      'Custom weapon attachment framework',
      'Realistic ballistics and recoil configuration',
      'Easy drop-in integration with existing mods',
      'Comprehensive XML documentation included',
      'Regular balance updates',
    ],
    changelog: [
      { version: '2.1.0', date: '2026-03-20', notes: 'Overhauled attachment slot system with new API hooks.' },
      { version: '2.0.0', date: '2026-01-10', notes: 'Major rewrite — improved API, better compatibility.' },
      { version: '1.0.0', date: '2025-10-05', notes: 'Initial release.' },
    ],
  },
  'battle-pass': {
    images: [
      '/products/battle-pass.png',
      '/products/battle-pass-2.png',
      '/products/battle-pass-3.png',
    ],
    tagline: 'Season-based progression system for DayZ servers.',
    features: [
      'Configurable season tiers and rewards',
      'XP and challenge tracking system',
      'Leaderboard and ranking support',
      'Easy JSON configuration',
      'Works alongside other mods',
    ],
    changelog: [
      { version: '1.1.0', date: '2026-04-10', notes: 'Added challenge system and extra reward tiers.' },
      { version: '1.0.0', date: '2026-02-20', notes: 'Initial release.' },
    ],
  },
};

export default function ProductDetailView({
  product,
  cart,
  onAddToCart,
  onBuy,
  onBack,
  shopLoading,
  accountSetupComplete,
}) {
  const [slideIndex, setSlideIndex] = useState(0);
  const [isAuto, setIsAuto] = useState(true);
  const [imgErrors, setImgErrors] = useState(new Set());
  const autoTimerRef = useRef(null);

  const meta = productMeta[product.id] || {};
  const price = `${(product.amountCents / 100).toFixed(2)} ${String(product.currency || '').toUpperCase()}`;
  const inCart = cart.includes(product.id);
  const images = meta.images?.length ? meta.images : [`/products/${product.id}.jpg`];

  // Reset when product changes
  useEffect(() => {
    setSlideIndex(0);
    setIsAuto(true);
    setImgErrors(new Set());
  }, [product.id]);

  // Auto-cycle
  useEffect(() => {
    if (!isAuto || images.length <= 1) return;
    autoTimerRef.current = setInterval(() => {
      setSlideIndex((i) => (i + 1) % images.length);
    }, 4000);
    return () => clearInterval(autoTimerRef.current);
  }, [isAuto, images.length]);

  const goTo = (idx) => {
    setSlideIndex(((idx % images.length) + images.length) % images.length);
  };

  const handleImageClick = () => {
    if (isAuto) {
      setIsAuto(false);
    } else {
      goTo(slideIndex + 1);
    }
  };

  const handleArrow = (e, delta) => {
    e.stopPropagation();
    setIsAuto(false);
    goTo(slideIndex + delta);
  };

  const handleDot = (e, idx) => {
    e.stopPropagation();
    setIsAuto(false);
    goTo(idx);
  };

  return (
    <section style={{ marginTop: '0.5rem' }}>
      {/* Back */}
      <button
        type="button"
        onClick={onBack}
        style={{
          background: 'transparent', border: 'none', color: '#8ab4f8',
          cursor: 'pointer', padding: '0 0 1rem 0', fontSize: '0.9rem', fontFamily: 'inherit',
        }}
      >
        ← Back to Shop
      </button>

      {/* Carousel */}
      <div
        role="img"
        aria-label={`${product.name} screenshots`}
        onClick={images.length > 1 ? handleImageClick : undefined}
        style={{
          width: '100%', maxWidth: 760, marginBottom: '1.5rem',
          borderRadius: 8, overflow: 'hidden',
          background: '#1e1e2e', border: '1px solid #333',
          aspectRatio: '16/9', position: 'relative',
          cursor: images.length > 1 ? 'pointer' : 'default',
          userSelect: 'none',
        }}
      >
        {/* Current slide */}
        {imgErrors.has(slideIndex) ? (
          <div style={{
            width: '100%', height: '100%', display: 'flex', flexDirection: 'column',
            alignItems: 'center', justifyContent: 'center', gap: '0.5rem', color: '#444',
          }}>
            <span style={{ fontSize: '2.5rem' }}>🖼</span>
            <span style={{ fontSize: '0.85rem' }}>Place image at /public{images[slideIndex]}</span>
          </div>
        ) : (
          <img
            key={images[slideIndex]}
            src={images[slideIndex]}
            alt={`${product.name} ${slideIndex + 1}`}
            onError={() => setImgErrors((prev) => new Set(prev).add(slideIndex))}
            style={{ width: '100%', height: '100%', objectFit: 'cover', display: 'block' }}
          />
        )}

        {images.length > 1 && (
          <>
            {/* Left arrow */}
            <button
              type="button"
              aria-label="Previous image"
              onClick={(e) => handleArrow(e, -1)}
              style={carouselArrowStyle('left')}
            >
              ‹
            </button>

            {/* Right arrow */}
            <button
              type="button"
              aria-label="Next image"
              onClick={(e) => handleArrow(e, 1)}
              style={carouselArrowStyle('right')}
            >
              ›
            </button>

            {/* Mode badge */}
            <div style={{
              position: 'absolute', top: 8, right: 8, pointerEvents: 'none',
              background: isAuto ? 'rgba(30,80,180,0.75)' : 'rgba(50,50,60,0.8)',
              color: '#ddd', fontSize: '0.65rem', letterSpacing: '0.05em',
              padding: '2px 8px', borderRadius: 10,
            }}>
              {isAuto ? 'AUTO' : 'MANUAL'}
            </div>

            {/* Dot indicators */}
            <div style={{
              position: 'absolute', bottom: 10, left: '50%', transform: 'translateX(-50%)',
              display: 'flex', gap: 6,
            }}>
              {images.map((_, i) => (
                <div
                  key={i}
                  role="button"
                  aria-label={`Go to image ${i + 1}`}
                  tabIndex={0}
                  onClick={(e) => handleDot(e, i)}
                  onKeyDown={(e) => e.key === 'Enter' && handleDot(e, i)}
                  style={{
                    width: 8, height: 8, borderRadius: '50%', cursor: 'pointer',
                    background: i === slideIndex ? '#cdf' : 'rgba(255,255,255,0.28)',
                    transition: 'background 0.2s',
                  }}
                />
              ))}
            </div>
          </>
        )}
      </div>

      {/* Title + actions row */}
      <div style={{
        display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start',
        flexWrap: 'wrap', gap: '1rem', marginBottom: '1.25rem', maxWidth: 760,
      }}>
        <div>
          <h2 style={{ margin: '0 0 0.25rem 0' }}>{product.name}</h2>
          {meta.tagline && (
            <p style={{ margin: 0, color: '#aaa', fontSize: '0.95rem' }}>{meta.tagline}</p>
          )}
        </div>
        <div style={{ display: 'flex', flexDirection: 'column', alignItems: 'flex-end', gap: '0.5rem' }}>
          <p style={{ margin: 0, color: '#cdf', fontSize: '1.2rem', fontWeight: 700 }}>{price}</p>
          <div style={{ display: 'flex', gap: '0.5rem' }}>
            <button
              type="button"
              disabled={inCart}
              onClick={() => onAddToCart(product.id)}
              style={{
                background: inCart ? '#1e3a1e' : '#1e2a4a',
                border: `1px solid ${inCart ? '#3a7a3a' : '#3a5a8e'}`,
                color: inCart ? '#8f8' : '#adf',
                borderRadius: 4, padding: '5px 14px',
                cursor: inCart ? 'default' : 'pointer',
                fontSize: '0.85rem', fontFamily: 'inherit',
              }}
            >
              {inCart ? '✓ In Cart' : '+ Add to Cart'}
            </button>
            <button
              type="button"
              disabled={shopLoading || !accountSetupComplete}
              onClick={() => onBuy(product.id)}
            >
              Buy Now
            </button>
          </div>
          {!accountSetupComplete && (
            <p style={{ margin: 0, color: '#f4a261', fontSize: '0.78rem', textAlign: 'right' }}>
              Link your Steam account before buying.
            </p>
          )}
        </div>
      </div>

      {/* About */}
      <div style={cardStyle}>
        <h3 style={cardHeadingStyle}>About</h3>
        <p style={{ margin: 0, color: '#ccc', lineHeight: 1.7 }}>{product.description}</p>
      </div>

      {/* Features */}
      {meta.features?.length > 0 && (
        <div style={cardStyle}>
          <h3 style={cardHeadingStyle}>Features</h3>
          <ul style={{ margin: 0, paddingLeft: '1.25rem', color: '#ccc', lineHeight: 1.9 }}>
            {meta.features.map((f) => (
              <li key={f}>{f}</li>
            ))}
          </ul>
        </div>
      )}

      {/* Changelog */}
      {meta.changelog?.length > 0 && (
        <div style={cardStyle}>
          <h3 style={cardHeadingStyle}>Changelog</h3>
          {meta.changelog.map((entry, i) => (
            <div
              key={entry.version}
              style={{
                borderTop: i > 0 ? '1px solid #2a2a3e' : 'none',
                paddingTop: i > 0 ? '0.65rem' : 0,
                marginTop: i > 0 ? '0.65rem' : 0,
              }}
            >
              <div style={{ display: 'flex', gap: '0.75rem', alignItems: 'center', marginBottom: '0.2rem' }}>
                <span style={{ color: '#cdf', fontWeight: 600, fontSize: '0.9rem' }}>v{entry.version}</span>
                <span style={{ color: '#555', fontSize: '0.8rem' }}>{entry.date}</span>
              </div>
              <p style={{ margin: 0, color: '#aaa', fontSize: '0.85rem' }}>{entry.notes}</p>
            </div>
          ))}
        </div>
      )}
    </section>
  );
}

const cardStyle = {
  background: '#1e1e2e',
  border: '1px solid #333',
  borderRadius: 8,
  padding: '1rem',
  marginBottom: '1rem',
  maxWidth: 760,
};

const cardHeadingStyle = {
  marginTop: 0,
  marginBottom: '0.6rem',
  fontSize: '1rem',
  color: '#cdf',
};

const carouselArrowStyle = (side) => ({
  position: 'absolute',
  top: '50%',
  transform: 'translateY(-50%)',
  [side]: 8,
  zIndex: 2,
  background: 'rgba(0,0,0,0.55)',
  border: 'none',
  color: '#fff',
  fontSize: '2rem',
  width: 38,
  height: 38,
  borderRadius: '50%',
  display: 'flex',
  alignItems: 'center',
  justifyContent: 'center',
  cursor: 'pointer',
  lineHeight: 1,
  fontFamily: 'inherit',
  padding: 0,
});
