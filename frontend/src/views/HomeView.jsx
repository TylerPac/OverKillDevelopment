
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

  const slides = [
    "/products/ok-dev.png",
    { type: 'medal', id: 'lZUzH7VRXRljEiAcw' },
    { type: 'medal', id: 'jthuMbIhAauv-jI4p' },
  ];

  const isVideoSlide = (slide) => typeof slide === 'object' && (slide.type === 'youtube' || slide.type === 'medal');
  const getVideoEmbedUrl = (slide) => {
    if (slide.type === 'youtube') return `https://www.youtube.com/embed/${slide.id}?rel=0`;
    if (slide.type === 'medal') return `https://medal.tv/clip/${slide.id}/renderer`;
    return '';
  };
  const currentSlide = slides[slideIndex];

  useEffect(() => {
    setSlideIndex(0);
    setIsAuto(true);
    setImgErrors(new Set());
  }, []);

  // Pause auto-cycle when on a video slide
  useEffect(() => {
    if (isVideoSlide(slides[slideIndex])) setIsAuto(false);
  }, [slideIndex]); // eslint-disable-line react-hooks/exhaustive-deps

  useEffect(() => {
    if (!isAuto || slides.length <= 1) return;
    autoTimerRef.current = setInterval(() => {
      setSlideIndex((i) => {
        const next = (i + 1) % slides.length;
        if (isVideoSlide(slides[next])) { clearInterval(autoTimerRef.current); return next; }
        return next;
      });
    }, 4000);
    return () => clearInterval(autoTimerRef.current);
  }, [isAuto, slides.length]); // eslint-disable-line react-hooks/exhaustive-deps

  const goTo = (idx) => {
    setSlideIndex(((idx % slides.length) + slides.length) % slides.length);
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
        <a
          href="https://discord.gg/Ak5SCtaYQS"
          target="_blank"
          rel="noopener noreferrer"
          style={{
            display: 'inline-flex', alignItems: 'center', gap: '0.5rem',
            marginTop: '0.4rem', padding: '0.5rem 1.2rem',
            background: '#5865F2', color: '#fff',
            borderRadius: 6, textDecoration: 'none',
            fontWeight: 600, fontSize: '0.95rem',
            transition: 'opacity 0.2s',
          }}
          onMouseEnter={e => e.currentTarget.style.opacity = '0.85'}
          onMouseLeave={e => e.currentTarget.style.opacity = '1'}
        >
          <svg width="20" height="20" viewBox="0 0 24 24" fill="currentColor" aria-hidden="true">
            <path d="M20.317 4.37a19.791 19.791 0 0 0-4.885-1.515.074.074 0 0 0-.079.037c-.21.375-.444.864-.608 1.25a18.27 18.27 0 0 0-5.487 0 12.64 12.64 0 0 0-.617-1.25.077.077 0 0 0-.079-.037A19.736 19.736 0 0 0 3.677 4.37a.07.07 0 0 0-.032.027C.533 9.046-.32 13.58.099 18.057a.082.082 0 0 0 .031.057 19.9 19.9 0 0 0 5.993 3.03.078.078 0 0 0 .084-.028 14.09 14.09 0 0 0 1.226-1.994.076.076 0 0 0-.041-.106 13.107 13.107 0 0 1-1.872-.892.077.077 0 0 1-.008-.128 10.2 10.2 0 0 0 .372-.292.074.074 0 0 1 .077-.01c3.928 1.793 8.18 1.793 12.062 0a.074.074 0 0 1 .078.01c.12.098.246.198.373.292a.077.077 0 0 1-.006.127 12.299 12.299 0 0 1-1.873.892.077.077 0 0 0-.041.107c.36.698.772 1.362 1.225 1.993a.076.076 0 0 0 .084.028 19.839 19.839 0 0 0 6.002-3.03.077.077 0 0 0 .032-.054c.5-5.177-.838-9.674-3.549-13.66a.061.061 0 0 0-.031-.03z"/>
          </svg>
          Join our Discord
        </a>
      </div>
      {/* Carousel */}
      <div
        role="img"
        aria-label="Home View Screenshot carousel"
        onClick={slides.length > 1 && !isVideoSlide(currentSlide) ? handleImageClick : undefined}
        style={{
          width: '100%', maxWidth: 760, margin: '1.5rem 0',
          borderRadius: 8, overflow: 'hidden',
          background: '#1e1e2e', border: '1px solid #333',
          aspectRatio: '16/9', position: 'relative',
          cursor: slides.length > 1 && !isVideoSlide(currentSlide) ? 'pointer' : 'default',
          userSelect: 'none',
        }}
      >
        {/* Current slide */}
        {isVideoSlide(currentSlide) ? (
          <iframe
            key={currentSlide.id}
            src={getVideoEmbedUrl(currentSlide)}
            title={`Home video ${slideIndex + 1}`}
            allow="accelerometer; autoplay; clipboard-write; encrypted-media; gyroscope; picture-in-picture"
            allowFullScreen
            style={{ width: '100%', height: '100%', border: 'none', display: 'block' }}
          />
        ) : imgErrors.has(slideIndex) ? (
          <div style={{
            width: '100%', height: '100%', display: 'flex', flexDirection: 'column',
            alignItems: 'center', justifyContent: 'center', gap: '0.5rem', color: '#444',
          }}>
            <span style={{ fontSize: '2.5rem' }}>🖼</span>
            <span style={{ fontSize: '0.85rem' }}>Place image at /public{currentSlide}</span>
          </div>
        ) : (
          <img
            key={currentSlide}
            src={currentSlide}
            alt={`Home View Screenshot ${slideIndex + 1}`}
            onError={() => setImgErrors((prev) => new Set(prev).add(slideIndex))}
            style={{ width: '100%', height: '100%', objectFit: 'cover', display: 'block' }}
          />
        )}

        {slides.length > 1 && (
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

            {/* Mode badge — hide when on a video slide */}
            {!isVideoSlide(currentSlide) && (
              <div style={{
                position: 'absolute', top: 8, right: 8, pointerEvents: 'none',
                background: isAuto ? 'rgba(30,80,180,0.75)' : 'rgba(50,50,60,0.8)',
                color: '#ddd', fontSize: '0.65rem', letterSpacing: '0.05em',
                padding: '2px 8px', borderRadius: 10,
              }}>
                {isAuto ? 'AUTO' : 'MANUAL'}
              </div>
            )}

            {/* Dot indicators */}
            <div style={{
              position: 'absolute', bottom: 10, left: '50%', transform: 'translateX(-50%)',
              display: 'flex', gap: 6, alignItems: 'center',
            }}>
              {slides.map((slide, i) => (
                isVideoSlide(slide) ? (
                  <div
                    key={i}
                    role="button"
                    aria-label={`Go to video ${i + 1}`}
                    tabIndex={0}
                    onClick={(e) => handleDot(e, i)}
                    onKeyDown={(e) => e.key === 'Enter' && handleDot(e, i)}
                    style={{
                      width: 16, height: 16, borderRadius: '50%', cursor: 'pointer',
                      background: i === slideIndex ? 'rgba(255,80,80,0.9)' : 'rgba(255,255,255,0.28)',
                      display: 'flex', alignItems: 'center', justifyContent: 'center',
                      fontSize: '0.5rem', color: '#fff', transition: 'background 0.2s',
                    }}
                  >
                    ▶
                  </div>
                ) : (
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
                )
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