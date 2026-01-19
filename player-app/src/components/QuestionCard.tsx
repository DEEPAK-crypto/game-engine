import { Check, X } from 'lucide-react';
import type { Question } from '@/types';

interface QuestionCardProps {
  question: Question;
  questionNumber: number;
  totalQuestions: number;
  selectedOption: number | null;
  submitted: boolean;
  correctOptionIndex: number | null;
  onSelectOption: (index: number) => void;
}

export function QuestionCard({
  question,
  questionNumber,
  totalQuestions,
  selectedOption,
  submitted,
  correctOptionIndex,
  onSelectOption,
}: QuestionCardProps) {
  const optionLabels = ['A', 'B', 'C', 'D', 'E', 'F'];

  const getOptionClasses = (index: number) => {
    const baseClasses = 'option-button flex items-center gap-4';

    if (correctOptionIndex !== null) {
      // Question has ended, show correct/wrong
      if (index === correctOptionIndex) {
        return `${baseClasses} correct`;
      }
      if (index === selectedOption && index !== correctOptionIndex) {
        return `${baseClasses} wrong`;
      }
      return `${baseClasses} border-gray-200`;
    }

    if (selectedOption === index) {
      return `${baseClasses} selected`;
    }

    return `${baseClasses} border-gray-200 hover:border-primary-400`;
  };

  const getOptionIcon = (index: number) => {
    if (correctOptionIndex === null) return null;

    if (index === correctOptionIndex) {
      return <Check className="w-5 h-5 text-green-600" />;
    }
    if (index === selectedOption && index !== correctOptionIndex) {
      return <X className="w-5 h-5 text-red-600" />;
    }
    return null;
  };

  return (
    <div className="bg-white rounded-2xl shadow-lg p-6 md:p-8 fade-in">
      {/* Question Header */}
      <div className="flex items-center justify-between mb-6">
        <span className="text-sm font-medium text-gray-500">
          Question {questionNumber} of {totalQuestions}
        </span>
        <span className="text-sm font-medium text-primary-600 bg-primary-50 px-3 py-1 rounded-full">
          {question.points || 100} pts
        </span>
      </div>

      {/* Question Text */}
      <h2 className="text-xl md:text-2xl font-bold text-gray-900 mb-8">
        {question.questionText}
      </h2>

      {/* Options */}
      <div className="space-y-3">
        {question.options.map((option, index) => (
          <button
            key={index}
            onClick={() => onSelectOption(index)}
            disabled={submitted || correctOptionIndex !== null}
            className={getOptionClasses(index)}
          >
            <span className="w-10 h-10 flex items-center justify-center bg-gray-100 rounded-lg font-bold text-gray-600">
              {optionLabels[index]}
            </span>
            <span className="flex-1 font-medium text-gray-800">{option}</span>
            {getOptionIcon(index)}
          </button>
        ))}
      </div>

      {/* Submission Status */}
      {submitted && correctOptionIndex === null && (
        <div className="mt-6 text-center">
          <div className="inline-flex items-center gap-2 px-4 py-2 bg-primary-50 text-primary-700 rounded-full">
            <div className="w-2 h-2 bg-primary-500 rounded-full animate-pulse" />
            <span className="text-sm font-medium">Answer submitted, waiting for results...</span>
          </div>
        </div>
      )}
    </div>
  );
}
