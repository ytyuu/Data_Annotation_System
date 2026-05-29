import { createRoot } from 'react-dom/client';
import { BrowserRouter as Router, Routes, Route, Navigate } from 'react-router-dom';
import './styles.css';
import { ApiTest } from './pages/ApiTest';
import { RoleSelect } from './pages/RoleSelect';
import { Login } from './pages/Login';

function App() {
  return (
    <Router>
      <Routes>
        <Route path="/" element={<RoleSelect />} />
        <Route path="/login" element={<Login />} />
        <Route path="/api-test" element={<ApiTest />} />
        <Route path="*" element={<Navigate to="/" replace />} />
      </Routes>
    </Router>
  );
}

const rootNode = document.getElementById('root');

if (!rootNode) {
  throw new Error('Missing #root element');
}

createRoot(rootNode).render(<App />);
