/** @type {import('tailwindcss').Config} */
export default {
    darkMode: 'class',
    content: [
        "./index.html",
        "./src/**/*.{js,ts,jsx,tsx}",
    ],
    theme: {
        extend: {
            colors: {
                primary: '#0078D4', // Windows Blue
                secondary: '#f3f3f3',
                border: '#e5e5e5',
                background: '#ffffff',
                foreground: '#0f172a', // Slate 900
            }
        },
    },
    plugins: [],
}
