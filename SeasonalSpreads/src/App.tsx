import React, { useState } from "react";
import {
  SignedIn,
  SignedOut,
  UserButton,
  SignInButton,
  RedirectToSignIn,
  useUser,
} from "@clerk/clerk-react";
import SpreadsTab from "./tabs/SpreadsTab.tsx";
import GulfCoastDiffsTab from "./tabs/GulfCoastDiffsTab.tsx";
import ChicagoDiffsTab from "./tabs/ChicagoDiffsTab.tsx";
import TransitTimesTab from "./tabs/TransitTimesTab.tsx";
import MagellanTab from "./tabs/MagellanTab.tsx";
import "./styles/main.css";

type TabOption =
  | "Spreads"
  | "Gulf Coast Diffs"
  | "Chicago Diffs"
  | "Transit Times"
  | "Magellan";

const App: React.FC = () => {
  const [activeTab, setActiveTab] = useState<TabOption>("Spreads");
  const { user } = useUser(); // This provides the user object

  const tabs: TabOption[] = [
    "Spreads",
    "Gulf Coast Diffs",
    "Chicago Diffs",
    "Transit Times",
    "Magellan",
  ];

  const renderTabContent = () => {
    switch (activeTab) {
      case "Spreads":
        return <SpreadsTab />;
      case "Gulf Coast Diffs":
        return <GulfCoastDiffsTab />;
      case "Chicago Diffs":
        return <ChicagoDiffsTab />;
      case "Transit Times":
        return <TransitTimesTab />;
      case "Magellan":
        return <MagellanTab />;
      default:
        return null;
    }
  };

  return (
    <div className="app-container">
      {/* Header with auth controls */}
      <header className="app-header">
        <h1 className="app-title">Energy Futures Dashboard</h1>
        <div className="auth-controls">
          <SignedIn>
            <div className="user-greeting">
              <span>Welcome, {user?.firstName || "User"}</span>
              <UserButton afterSignOutUrl="/" />
            </div>
          </SignedIn>
          <SignedOut>
            <SignInButton mode="modal">
              <button className="sign-in-btn">Sign In</button>
            </SignInButton>
          </SignedOut>
        </div>
      </header>

      {/* Main content area */}
      <main className="app-main">
        <SignedIn>
          {/* Navigation tabs */}
          <nav className="tab-nav">
            {tabs.map((tab) => (
              <button
                key={tab}
                onClick={() => setActiveTab(tab)}
                className={`tab-btn ${activeTab === tab ? "active" : ""}`}
              >
                {tab}
              </button>
            ))}
          </nav>

          {/* Tab content */}
          <div className="tab-content">{renderTabContent()}</div>
        </SignedIn>

        <SignedOut>
          <div className="sign-in-prompt">
            <RedirectToSignIn />
          </div>
        </SignedOut>
      </main>

      {/* Footer */}
      <footer className="app-footer">
        <p>© {new Date().getFullYear()} Energy Futures Dashboard</p>
      </footer>
    </div>
  );
};

export default App;
