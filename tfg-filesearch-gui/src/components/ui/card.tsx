import React from 'react';
import { motion, HTMLMotionProps } from 'framer-motion';
import { cn } from '../../utils';

export interface CardProps extends HTMLMotionProps<"div"> {
    children: React.ReactNode;
    className?: string;
    hover?: boolean;
}

export const Card: React.FC<CardProps> = ({
    children,
    className,
    hover = false,
    ...props
}) => {
    return (
        <motion.div
            className={cn(
                "bg-white/80 dark:bg-gray-800/80 backdrop-blur-xl",
                "border border-gray-200/50 dark:border-gray-700/50",
                "rounded-xl shadow-lg",
                "transition-all duration-200",
                hover && "hover:shadow-xl hover:scale-[1.01]",
                className
            )}
            {...props}
        >
            {children}
        </motion.div>
    );
};
