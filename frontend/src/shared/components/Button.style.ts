export const buttonStyles = `
  .app-button {
    display: inline-flex;
    align-items: center;
    justify-content: center;
    gap: 10px;
    min-height: 44px;
    padding: 0 18px;
    border: 1px solid transparent;
    font: 800 12px/1 var(--font-sans);
    letter-spacing: -.01em;
    text-decoration: none;
    cursor: pointer;
    transition: filter .18s ease, border-color .18s ease, color .18s ease,
      opacity .18s ease;
  }

  .app-button--primary {
    border-color: var(--accent);
    background: var(--accent);
    color: var(--black);
  }

  .app-button--secondary {
    border-color: #4b4b4b;
    background: transparent;
    color: var(--white);
  }

  .app-button--ghost {
    border-color: #535353;
    background: transparent;
    color: var(--white);
  }

  .app-button--full {
    width: 100%;
  }

  .app-button:hover:not(:disabled):not([aria-disabled='true']) {
    filter: brightness(.95);
  }

  .app-button--secondary:hover:not(:disabled):not([aria-disabled='true']),
  .app-button--ghost:hover:not(:disabled):not([aria-disabled='true']) {
    border-color: var(--accent);
    color: var(--accent);
  }

  .app-button:disabled,
  .app-button[aria-disabled='true'] {
    cursor: not-allowed;
    opacity: .45;
  }
`;
