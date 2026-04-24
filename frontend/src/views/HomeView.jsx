
import React, { useState, useEffect, useRef } from 'react';


import ShopView from './ShopView';

export default function HomeView({
  authenticated,
  accountSetupComplete,
  shopLoading,
  products,
  orders,
  cart,
  onBuy,
  onDownload,
  onViewProduct,
  onAddToCart,
}) {
  const [slideIndex, setSlideIndex] = useState(0);
  const [isAuto, setIsAuto] = useState(true);
  const [imgErrors, setImgErrors] = useState(new Set());
  const autoTimerRef = useRef(null);

  const images = [
    "/products/battle-pass.png",
    "/products/battle-pass-2.png",
    "/products/battle-pass-3.png"
  ];

  useEffect(() => {
    setSlideIndex(0);
    setIsAuto(true);
    setImgErrors(new Set());
  }, []);

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
    <section style={{ width: '100%', display: 'flex', flexDirection: 'column', alignItems: 'center' }}>
      <div style={{ width: '100%', maxWidth: 760, textAlign: 'center', marginBottom: '1.2rem' }}>
        <h1 style={{ marginBottom: '0.7rem', color: '#cde3ff' }}>Welcome to OverKill Development</h1>
        <p style={{ color: '#aaa', marginTop: 0, fontSize: '1.08rem', lineHeight: 1.6 }}>
          Our mission is to bring the most immersive and realistic DayZ experience to players worldwide. With a strong focus on performance, our mods are designed to enhance your gameplay without compromising your PC's resources.<br />
          Whether you're a seasoned veteran or just starting out, we invite you to explore our collection of high-quality mods and join our community today!
        </p>
      </div>
      {/* Carousel */}
      <div
        role="img"
        aria-label="Home View Screenshot carousel"
        onClick={images.length > 1 ? handleImageClick : undefined}
        style={{
          width: '100%', maxWidth: 760, margin: '1.5rem 0',
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
            alt={`Home View Screenshot ${slideIndex + 1}`}
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

      

      {/* Shop below welcome/hero */}
      <div style={{ width: '100%', maxWidth: 760 }}>
        <ShopView
          authenticated={authenticated}
          accountSetupComplete={accountSetupComplete}
          shopLoading={shopLoading}
          products={products}
          orders={orders}
          cart={cart}
          onBuy={onBuy}
          onDownload={onDownload}
          onViewProduct={onViewProduct}
          onAddToCart={onAddToCart}
          showHeading={false}
        />
      </div>
    </section>
  );
}

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