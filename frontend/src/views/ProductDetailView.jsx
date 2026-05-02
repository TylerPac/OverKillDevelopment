import { useState, useEffect, useRef } from 'react';

// Showcase metadata per product — update text/features/changelog as mods evolve
const productMeta = {
  'keycard-crates': {
    slides: [
      '/products/keycard-crates.png',
      '/products/keycard-crates-2.png',
      '/products/keycard-crates-3.png',
      
      // Video examples:
      // { type: 'youtube', id: 'dQw4w9WgXcQ' }
      // { type: 'medal', id: 'jthuMbIhAauv-jI4p' }
    ],
    tagline: 'A complete, performance-optimized keycard and dynamic crate ecosystem for DayZ — built for server owners who want depth without complexity.',
    about: 'KeyCard Crates is a fully modular gameplay system that introduces keycards, locked doors, dynamic loot crates, and server-driven events into your DayZ server — all without requiring manual placement or coding. Designed for scalability and performance, the system can automatically populate your map with 15,000+ crates, integrate seamlessly with map edits, and give you full control over loot progression, player interaction, and event flow.',
    featureSections: [
      {
        title: '🗺️ Automated Crate Population',
        items: [
          'Instantly populate your map with thousands of optimized crate spawns',
          'Works out-of-the-box with vanilla maps and custom map edits (DayZ Editor compatible)',
          'No manual placement required',
        ],
      },
      {
        title: '🎨 Tier Painting System (Web Tool Integration)',
        items: [
          'Use your custom web-based painting tool to define loot tiers visually',
          'Paint crate tiers just like vanilla loot zones',
          'Real-time workflow — export directly to config',
        ],
      },
      {
        title: '🔐 Keycard & Locked Door System',
        items: [
          'Lock any vanilla or custom door',
          'Assign keycards or physical keys per location',
          'Supports usage limits, alarm triggers, relock timers, and linked door groups',
        ],
      },
      {
        title: '💣 Dynamic & Hackable Crate Events',
        items: [
          'Event-driven crate system with player count requirements and timed unlock sequences',
          'Multi-stage notifications and immersive event flow',
          'Fully configurable timers and behavior',
        ],
      },
      {
        title: '📦 Advanced Loot System',
        items: [
          'Modular loot tables, item profiles, and attachment profiles',
          'Per-tier loot balancing',
          'Attachment spawning logic — weapons spawn correctly with parts',
        ],
      },
      {
        title: '🛠️ No-Code Configuration (Google Sheets Integration)',
        items: [
          'Generate full configs using a Google Spreadsheet tool',
          'Copy/paste ready JSON output',
          'Create multiple loot setups instantly',
        ],
      },
      {
        title: '📡 Server Feedback & Notifications',
        items: [
          'Global or proximity-based notifications',
          'Event announcements for hackable crates, doors, and more',
          'Immersion-focused alert system',
        ],
      },
      {
        title: '⚡ Performance First',
        items: [
          'Built for high player-count servers',
          'Optimized spawn logic and caching',
          'Minimal server overhead even with large-scale systems',
        ],
      },
    ],
    whyDifferent: [
      'Map-wide automated crate population',
      'Visual tier editing via web tool',
      'Door + keycard gameplay integration',
      'Event-driven dynamic crates',
      'Designed for server economies, progression, and replayability',
    ],
    changelog: [
    {
      version: '1.1.0',
      date: '2026-04-01',
      notes: 'Official public release. Introduced full keycard, crate, and event system with web-based tier painting support.'
    },
    {
      version: '1.2.0',
      date: '2026-02-15',
      notes: 'Major performance optimizations, reduced server overhead, and improved system stability.'
    },
    {
      version: '1.3.0',
      date: '2026-04-01',
      notes: 'Added new crate variants, and enhanced hackable event system.'
    }
  ],
  },
  'weapon-system': {
    slides: [
      '/products/weapon-system.png',
      '/products/weapon-system-2.png',
      '/products/weapon-system-3.png',
      { type: 'medal', id: 'lZUzH7VRXRljEiAcw' },
    ],
    tagline: 'A fully modular weapon attachment system for DayZ — 150+ attachments, 100+ firearms, and true part-on-part modularity that feels immersive, not crafted.',
    about: 'The Weapon System is a comprehensive, performance-optimized firearms mod built from the ground up for true modularity. Vertical grips attach to handguards, handguards attach to your rifle — every piece slots together the way it would in real life. With over 150 attachments and 100+ weapons, this is not a reskin pack. It\'s an ecosystem.',
    featureSections: [
      {
        title: '🔩 True Part-on-Part Modularity',
        items: [
          '150+ attachments: handguards, optics, grips, vertical grips, charging handles, and more',
          '100+ firearms — all fully interchangeable',
          'Attachments mount onto attachments (e.g. vertical grip → handguard → rifle) for genuine modularity',
          'No crafting menus — everything feels natural and immersive',
        ],
      },
      {
        title: '🔭 Full Optics System',
        items: [
          'Holographic sights, red dots, and long-range magnified optics',
          'Customizable reticle color — choose the reticle color that fits your playstyle or visibility preference',
          'Customizable reticle style — select from multiple reticle designs on supported optics',
          'Night vision compatible IR lasers that project a visible beam in-game',
          'Standard visible lasers also supported',
        ],
      },
      {
        title: '🦺 Deployable Bipods',
        items: [
          'Bipods deploy dynamically — not just when prone',
          'Works against window sills, and obstacles for realistic supported fire',
          'Actively reduces recoil when deployed',
        ],
      },
      {
        title: '🎯 Custom ADS System',
        items: [
          'Per-attachment ADS speed tuning built directly into the mod',
          'Heavier setups feel sluggish; lightweight builds snap up faster',
          'No external dependency required',
        ],
      },
      {
        title: '🤝 Compatibility',
        items: [
          'Full A6 compatibility',
          'Optional sGunplay integration for enhanced gunfeel — not required',
          'Designed to work alongside other mods without conflict',
        ],
      },
      {
        title: '⚡ Performance Optimized',
        items: [
          'All models built with proper LODs (Level of Detail) for minimal server and client overhead',
          'Optimized for high player-count servers',
          'Clean, well-structured code — easy for server owners to configure',
        ],
      },
    ],
    changelog: [
      { version: '2.1.0', date: '2026-03-20', notes: 'Added deployable bipod system with obstacle detection and recoil reduction. Added IR laser variants.' },
      { version: '2.0.0', date: '2026-01-10', notes: 'Full rewrite — introduced part-on-part attachment system, custom ADS speed, and sGunplay compatibility.' },
      { version: '1.0.0', date: '2025-10-05', notes: 'Initial release with 80+ weapons and 100+ attachments.' },
    ],
  },
  'battle-pass': {
    slides: [
      '/products/battle-pass.png',
      '/products/battle-pass-2.png',
      '/products/battle-pass-3.png',
      { type: 'medal', id: 'jthuMbIhAauv-jI4p' },
    ],
    tagline: 'A fully modular, Bohemia-compliant battle pass for DayZ — free or paid tiers, XP progression, weapon mastery, and cosmetic rewards that keep your server engaging without paying for power.',
    about: 'The Battle Pass is a season-based progression system built for DayZ servers that want deep player engagement without breaking Bohemia\'s monetization rules. Players earn XP through kills, quests, and animal hunting to climb tiers — unlocking cosmetics, gear slots, and rewards along the way. The free/paid split is fully configurable: you decide what\'s gated and what\'s open. Everything paid is cosmetic. Nothing pays to win.',
    featureSections: [
      {
        title: '🎖️ XP & Tier Progression',
        items: [
          'Players level up via XP earned through PvP kills, animal kills, and questing',
          'Expansion Quests (DayZ Expansion) compatibility for quest-based XP',
          'Configurable XP values per activity — fully server-owner controlled',
          'Lock market purchases and quest access behind tier requirements',
          'Expansion Market compatibility — gate shop access by battle pass level',
        ],
      },
      {
        title: '🆓 Free & Paid Tiers',
        items: [
          'Run the battle pass as entirely free — no paid version required',
          'Or enable paid tiers where premium items are cosmetic-only',
          'Free players and paid players can both receive the same items via the reward menu — just set items as free to stay compliant',
          'All paid rewards are cosmetic, keeping within Bohemia\'s monetization policy',
          'Secure containers, skins, and equippable items can be distributed through the pass without pay-to-win concerns',
        ],
      },
      {
        title: '🎁 Reward System',
        items: [
          'Reward players at specific tier milestones — equippable items, cosmetics, containers, and more',
          'Example: unlock and equip progressively better secure containers as you level up',
          'Players equip rewards directly through the battle pass UI — no external menu needed',
          'Full control over what is free vs. paid at each tier',
        ],
      },
      {
        title: '🔫 Weapon Mastery',
        items: [
          'Secondary progression track — kills with each weapon class level up that mastery',
          'Weapon classes: assault rifles, shotguns, pistols, and more',
          'Mastery rewards are configurable: reduce recoil stats as a class levels up, or unlock custom weapon skins',
          'Example: master assault rifles to unlock gold AR skins — unlock specific gold variants at specific mastery levels',
          'Each weapon class is tracked independently, encouraging diverse playstyles',
        ],
      },
      {
        title: '⚙️ Modular Configuration',
        items: [
          'Every aspect is configurable — XP rates, tier counts, reward items, mastery thresholds',
          'Enable or disable weapon mastery, free/paid split, questing, or market gating independently',
          'Server owners can run a minimal setup or go all in — the system adapts to your server vision',
          'Clean, well-documented config files — no coding required',
        ],
      },
    ],
    whyDifferent: [
      'Designed around Bohemia\'s monetization rules from the ground up — not retrofitted',
      'Dual progression (tier XP + weapon mastery) gives players more to work toward at all times',
      'The free/paid split lets you monetize your server without creating a pay-to-win environment',
      'Deep compatibility with DayZ Expansion — quests, market, and containers all integrate cleanly',
      'Fully modular — run it as a simple free reward track, or build a full seasonal battle pass experience',
    ],
    changelog: [
      { version: '1.2.0', date: '2026-04-10', notes: 'Added weapon mastery system with per-class kill tracking, recoil modifiers, and skin unlocks.' },
      { version: '1.1.0', date: '2026-03-01', notes: 'Added Expansion Market gating, quest XP compatibility, and secure container reward support.' },
      { version: '1.0.0', date: '2026-02-20', notes: 'Initial release with XP progression, free/paid tier split, and configurable reward milestones.' },
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
  const slides = meta.slides?.length ? meta.slides
    : meta.images?.length ? meta.images
    : [`/products/${product.id}.jpg`];

  const isVideoSlide = (slide) => typeof slide === 'object' && (slide.type === 'youtube' || slide.type === 'medal');
  const getVideoEmbedUrl = (slide) => {
    if (slide.type === 'youtube') return `https://www.youtube.com/embed/${slide.id}?rel=0`;
    if (slide.type === 'medal') return `https://medal.tv/clip/${slide.id}/renderer`;
    return '';
  };
  const currentSlide = slides[slideIndex];

  // Reset when product changes
  useEffect(() => {
    setSlideIndex(0);
    setIsAuto(true);
    setImgErrors(new Set());
  }, [product.id]);

  // Pause auto-cycle when on a video slide
  useEffect(() => {
    if (isVideoSlide(slides[slideIndex])) setIsAuto(false);
  }, [slideIndex]); // eslint-disable-line react-hooks/exhaustive-deps

  // Auto-cycle
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
        aria-label={`${product.name} screenshots`}
        onClick={slides.length > 1 && !isVideoSlide(currentSlide) ? handleImageClick : undefined}
        style={{
          width: '100%', maxWidth: 760, marginBottom: '1.5rem',
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
            title={`${product.name} video ${slideIndex + 1}`}
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
            alt={`${product.name} ${slideIndex + 1}`}
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

      {/* Title + actions */}
      <div style={{ maxWidth: 760, marginBottom: '1.25rem' }}>
        <h2 style={{ margin: '0 0 0.25rem 0' }}>{product.name}</h2>
        {meta.tagline && (
          <p style={{ margin: '0 0 0.75rem 0', color: '#aaa', fontSize: '0.95rem' }}>{meta.tagline}</p>
        )}
        <p style={{ margin: '0 0 0.5rem 0', color: '#cdf', fontSize: '1.2rem', fontWeight: 700 }}>{price}</p>
        <div style={{ display: 'flex', gap: '0.5rem', alignItems: 'center', flexWrap: 'wrap' }}>
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
          {!accountSetupComplete && (
            <p style={{ margin: 0, color: '#f4a261', fontSize: '0.78rem' }}>
              Link your Steam account before buying.
            </p>
          )}
        </div>
      </div>

      {/* About */}
      <div style={cardStyle}>
        <h3 style={cardHeadingStyle}>About</h3>
        <p style={{ margin: 0, color: '#ccc', lineHeight: 1.7 }}>{meta.about || product.description}</p>
      </div>

      {/* Features — grouped sections */}
      {meta.featureSections?.length > 0 && (
        <div style={cardStyle}>
          <h3 style={cardHeadingStyle}>⚙️ Core Features</h3>
          {meta.featureSections.map((section) => (
            <div key={section.title} style={{ marginBottom: '1rem' }}>
              <p style={{ margin: '0 0 0.3rem 0', color: '#cdf', fontWeight: 600, fontSize: '0.95rem' }}>{section.title}</p>
              <ul style={{ margin: 0, paddingLeft: '1.25rem', color: '#ccc', lineHeight: 1.8 }}>
                {section.items.map((item) => (
                  <li key={item}>{item}</li>
                ))}
              </ul>
            </div>
          ))}
        </div>
      )}

      {/* Features — flat list (fallback) */}
      {!meta.featureSections && meta.features?.length > 0 && (
        <div style={cardStyle}>
          <h3 style={cardHeadingStyle}>Features</h3>
          <ul style={{ margin: 0, paddingLeft: '1.25rem', color: '#ccc', lineHeight: 1.9 }}>
            {meta.features.map((f) => (
              <li key={f}>{f}</li>
            ))}
          </ul>
        </div>
      )}

      {/* Why This Is Different */}
      {meta.whyDifferent?.length > 0 && (
        <div style={cardStyle}>
          <h3 style={cardHeadingStyle}>🧩 Why This Is Different</h3>
          <p style={{ margin: '0 0 0.5rem 0', color: '#aaa', fontSize: '0.9rem' }}>No other system combines:</p>
          <ul style={{ margin: 0, paddingLeft: '1.25rem', color: '#ccc', lineHeight: 1.9 }}>
            {meta.whyDifferent.map((item) => (
              <li key={item}>{item}</li>
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
