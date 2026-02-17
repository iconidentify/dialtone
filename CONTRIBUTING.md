# Contributing to Dialtone

Thanks for your interest in contributing to Dialtone.

## Getting Started

1. Fork the repository
2. Create a feature branch from `main`
3. Make your changes
4. Run the test suite: `mvn clean test`
5. Open a pull request against `main`

## Development Setup

See the [Quick Start](README.md#quick-start) section for build prerequisites and configuration.

## Code Style

- Java 21 with standard library conventions
- No tabs -- use 4 spaces for indentation
- Keep methods focused and reasonably sized
- Add tests for new functionality

## Testing

All pull requests must pass the existing test suite. Property-based tests use Jqwik. Run the full suite with:

```bash
mvn clean test
```

For CI-equivalent testing with coverage enforcement:

```bash
mvn clean test -Pci
```

## Reporting Issues

Open an issue on GitHub with:
- A clear description of the problem or feature request
- Steps to reproduce (for bugs)
- Expected vs actual behavior
- Relevant log output or screenshots

## License

By contributing, you agree that your contributions will be licensed under the MIT License.
