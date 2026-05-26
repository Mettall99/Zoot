export default function Page() {
  const sections = ['Dashboard','Users','Subscriptions','Payments','Servers','Countries','Protocols','Tariffs','Connection sessions','Logs','Settings'];
  return (
    <main style={{ padding: 24, fontFamily: 'sans-serif' }}>
      <h1>Zooot VPN Admin</h1>
      <ul>{sections.map((s) => <li key={s}>{s}</li>)}</ul>
    </main>
  );
}
