#!/usr/bin/python
import select
import socket
import sys
import uuid
import os
import argparse

def bdecode(f, char=None):
    "Taken from vim-fireplace"
    if char == None:
        char = f.read(1)
    if char == 'l':
        l = []
        while True:
            char = f.read(1)
            if char == 'e':
                return l
            l.append(bdecode(f, char))
    elif char == 'd':
        d = {}
        while True:
            char = f.read(1)
            if char == 'e':
                return d
            key = bdecode(f, char)
            d[key] = bdecode(f)
    elif char == 'i':
        i = ''
        while True:
            char = f.read(1)
            if char == 'e':
                return int(i)
            i += char
    elif char.isdigit():
        i = int(char)
        while True:
            char = f.read(1)
            if char == ':':
                return f.read(i)
            i = 10 * i + int(char)
    elif char == '':
        raise EOFError("unexpected end of bencode data")
    else:
        raise TypeError("unexpected type "+char+" in bencode data")

def bencode(x):
    "Half-baked implementation of bencode that only covers the parts I need"
    if isinstance(x, dict):
        return "d{}e".format(''.join([bencode(y) for pair in sorted(x.items()) for y in pair]))
    elif isinstance(x, str):
        return "{}:{}".format(len(x), x)
    else:
        raise NotImplementedError("bencode not implemented for " + type(x))

def receive(socket, char=None):
    f = socket.makefile()
    while len(select.select([f], [], [], 0.1)[0]) == 0:
        pass
    try:
        return bdecode(f)
    finally:
        f.close()

def main(port, verbose, code):
    with socket.socket(socket.AF_INET, socket.SOCK_STREAM) as s:
        s.settimeout(8)
        s.connect(("localhost", int(port)))
        s.setblocking(1)

        selector = str(uuid.uuid4())
        payload = bencode({'code': code,
                           'id': selector,
                           'op': 'eval',
                           'nrepl.middleware.caught/print?': 'true'})
        if verbose:
            print("sending:", repr(payload), "\n")
        s.sendall(bytes(payload, 'UTF-8'))

        while True:
            response = receive(s)
            if verbose:
                print("received:", repr(response), "\n")
            if response["id"] != selector:
                continue
            if 'out' in response:
                print(response['out'], end="")
            if 'nrepl.middleware.caught/throwable' in response:
                print(response['nrepl.middleware.caught/throwable'])
            if 'value' in response:
                try:
                    ret = int(response['value'])
                except ValueError:
                    ret = 0
                sys.exit(ret)
            if 'status' in response and 'done' in set(response['status']):
                sys.exit(0)

def slurp(path):
    "why in the h*ck isn't this included with python"
    with open(path, 'r') as f:
        return f.read()

def default_port(directory=os.getcwd()):
    try:
        return int(slurp(os.path.join(directory, ".nrepl-port")))
    except:
        parent = os.path.dirname(directory)
        if parent != directory:
            return default_port(parent)
        print("Couldn't find .nrepl-port file")
        sys.exit(1)

def main_code(entry_point, *args):
    return ("(do (require '{}) ({} {}))))").format(
            entry_point.split('/')[0],
            entry_point,
            ' '.join(['"' + x.replace('"', '\\"') + '"' for x in args]))

if __name__ == "__main__":
    parser = argparse.ArgumentParser(description='Run some code in an nRepl connection.')
    parser.add_argument('-p', '--port', type=int, help='The nRepl port. If unspecified, '
                        'will attempt to read port from an .nrepl-port file (either in the current '
                        'directory or in an ancestor directory).')
    parser.add_argument('-v', '--verbose', action="store_true", help='Print messages to and from nrepl.')
    subparsers = parser.add_subparsers(dest="cmd")
    eval_parser = subparsers.add_parser('eval', help="Eval some code.")
    eval_parser.add_argument('code', help="e.g. \"(println \\\"hey\\\")\". The expression's value will be "
            "used as this script's exit code (if it's an integer).")
    main_parser = subparsers.add_parser('main', usage="%(prog)s entry_point [arg1 [arg2 ...]]",
            help="Run an existing function.")
    main_parser.add_argument('entry_point', help="The fully-qualified function name followed by any arguments, "
                        'e.g. `%(prog)s foo.core/bar hello 7`. All arguments will be passed as strings.')

    if 'main' in sys.argv:
        sys.argv.insert(sys.argv.index('main') + 2, '--')

    opts, args = parser.parse_known_args()

    port = opts.port or default_port()
    if opts.cmd == 'eval':
        main(port, opts.verbose, opts.code)
    elif opts.cmd == 'main':
        main(port, opts.verbose, main_code(opts.entry_point, *args))
