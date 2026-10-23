/** Reference copy only. Replace these illustrative terms with approved product information. */
export const WELCOME_CONTENT = {
  stats: [
    { value: '$600,000', description: 'Manage capital up to' },
    { value: 'Daily Live Trading', description: 'Top traders at your fingertips' },
    { value: '90%', description: 'Profit split ratio up to' },
    { value: '24 h', description: 'Avg payout processing time' },
  ],
  statsNote:
    'Illustrative programme terms from the design reference. Funding and payouts are not currently offered by this platform.',
  steps: [
    {
      number: '01',
      title: 'Create your account',
      description: 'Register for a TradeVelocity workspace.',
    },
    {
      number: '02',
      title: 'Explore your portfolio',
      description: 'Review balances, positions and performance in your dashboard.',
    },
    {
      number: '03',
      title: 'Put your skills to work',
      description: 'Access the order ticket, then follow your activity in the blotter.',
    },
  ],
  faqs: [
    {
      question: 'How do I access the trading workspace?',
      answer:
        'Create an account or sign in through the client area. Your dashboard brings together your portfolio, positions and trading tools.',
    },
    {
      question: 'Does signing up give me a funded account?',
      answer:
        'No. Registration creates a platform account. The funding, profit split and payout figures above are illustrative content from the reference design, not an available funding programme.',
    },
    {
      question: 'Can I change the appearance of my workspace?',
      answer:
        'Yes. Use the theme button in the navigation to switch between light and dark. Your preference is saved and carried through to the rest of the platform.',
    },
  ],
} as const;
