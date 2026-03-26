import type { ReactNode } from "react";
import { motion } from "framer-motion";

type SectionProps = {
  title: string;
  description?: string;
  aside?: ReactNode;
  children: ReactNode;
};

export function Section({ title, description, aside, children }: SectionProps) {
  return (
    <motion.section
      className="section-block"
      initial={{ opacity: 0, y: 18 }}
      animate={{ opacity: 1, y: 0 }}
      transition={{ duration: 0.35, ease: "easeOut" }}
    >
      <div className="section-header">
        <div>
          <h3>{title}</h3>
          {description ? <p>{description}</p> : null}
        </div>
        {aside ? <div className="section-aside">{aside}</div> : null}
      </div>
      <div className="section-content">{children}</div>
    </motion.section>
  );
}

