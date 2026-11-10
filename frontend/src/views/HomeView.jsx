
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

  const clickable = slides.length > 1 && !isVideoSlide(currentSlide);

  return (
    <>
      <section className="hero">
        <div className="hero-inner">
          <p className="kicker"><span className="tag">DayZ mods</span> Built for high-pop servers</p>
          <h1>Welcome to <em>OverKill</em> Development</h1>
          <p className="lede">
            Our mission is to bring the most immersive and realistic DayZ experience to players worldwide. With a strong focus on performance, our mods are designed to enhance your gameplay without compromising your PC&apos;s resources.
          </p>
          <p className="lede">
            Whether you&apos;re a seasoned veteran or just starting out, we invite you to explore our collection of high-quality mods and join our community today!
          </p>
          <div className="button-row">
            <a href="#products" className="btn btn-primary btn-lg">Browse Mods</a>
            <a
              href="https://discord.gg/Ak5SCtaYQS"
              target="_blank"
              rel="noopener noreferrer"
              className="btn btn-discord btn-lg"
            >
              <svg width="20" height="20" viewBox="0 0 24 24" fill="currentColor" aria-hidden="true">
                <path d="M20.317 4.37a19.791 19.791 0 0 0-4.885-1.515.074.074 0 0 0-.079.037c-.21.375-.444.864-.608 1.25a18.27 18.27 0 0 0-5.487 0 12.64 12.64 0 0 0-.617-1.25.077.077 0 0 0-.079-.037A19.736 19.736 0 0 0 3.677 4.37a.07.07 0 0 0-.032.027C.533 9.046-.32 13.58.099 18.057a.082.082 0 0 0 .031.057 19.9 19.9 0 0 0 5.993 3.03.078.078 0 0 0 .084-.028 14.09 14.09 0 0 0 1.226-1.994.076.076 0 0 0-.041-.106 13.107 13.107 0 0 1-1.872-.892.077.077 0 0 1-.008-.128 10.2 10.2 0 0 0 .372-.292.074.074 0 0 1 .077-.01c3.928 1.793 8.18 1.793 12.062 0a.074.074 0 0 1 .078.01c.12.098.246.198.373.292a.077.077 0 0 1-.006.127 12.299 12.299 0 0 1-1.873.892.077.077 0 0 0-.041.107c.36.698.772 1.362 1.225 1.993a.076.076 0 0 0 .084.028 19.839 19.839 0 0 0 6.002-3.03.077.077 0 0 0 .032-.054c.5-5.177-.838-9.674-3.549-13.66a.061.061 0 0 0-.031-.03z"/>
              </svg>
              Join our Discord
            </a>
          </div>
        </div>
      </section>

      <section className="section">
        <div className="section-head">
          <h2><span className="index">01</span>In the field</h2>
          <p>Screenshots &amp; clips</p>
        </div>
        {/* Carousel */}
        <div
          role="img"
          aria-label="Home View Screenshot carousel"
          onClick={clickable ? handleImageClick : undefined}
          className={clickable ? 'carousel clickable' : 'carousel'}
        >
          {/* Current slide */}
          {isVideoSlide(currentSlide) ? (
            <iframe
              key={currentSlide.id}
              src={getVideoEmbedUrl(currentSlide)}
              title={`Home video ${slideIndex + 1}`}
              allow="accelerometer; autoplay; clipboard-write; encrypted-media; gyroscope; picture-in-picture"
              allowFullScreen
            />
          ) : imgErrors.has(slideIndex) ? (
            <div className="carousel-missing">
              <span>🖼</span>
              <span>Place image at /public{currentSlide}</span>
            </div>
          ) : (
            <img
              key={currentSlide}
              src={currentSlide}
              alt={`Home View Screenshot ${slideIndex + 1}`}
              onError={() => setImgErrors((prev) => new Set(prev).add(slideIndex))}
            />
          )}

          {slides.length > 1 && (
            <>
              <button type="button" aria-label="Previous image" onClick={(e) => handleArrow(e, -1)} className="carousel-arrow left">
                ‹
              </button>
              <button type="button" aria-label="Next image" onClick={(e) => handleArrow(e, 1)} className="carousel-arrow right">
                ›
              </button>

              {/* Mode badge — hide when on a video slide */}
              {!isVideoSlide(currentSlide) && (
                <div className={isAuto ? 'carousel-mode auto' : 'carousel-mode'}>
                  {isAuto ? 'AUTO' : 'MANUAL'}
                </div>
              )}

              {/* Dot indicators */}
              <div className="carousel-dots">
                {slides.map((slide, i) => (
                  <div
                    key={i}
                    role="button"
                    aria-label={isVideoSlide(slide) ? `Go to video ${i + 1}` : `Go to image ${i + 1}`}
                    tabIndex={0}
                    onClick={(e) => handleDot(e, i)}
                    onKeyDown={(e) => e.key === 'Enter' && handleDot(e, i)}
                    className={`carousel-dot${isVideoSlide(slide) ? ' video' : ''}${i === slideIndex ? ' active' : ''}`}
                  >
                    {isVideoSlide(slide) ? '▶' : null}
                  </div>
                ))}
              </div>
            </>
          )}
        </div>
      </section>

      {/* Shop below welcome/hero */}
      <section className="section" id="products">
        <div className="section-head">
          <h2><span className="index">02</span>Mods</h2>
          <p>Click a product for details</p>
        </div>
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
      </section>
    </>
  );
}
